package io.bidvelocity.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bidvelocity.payment.client.AuctionOutboxClient;
import io.bidvelocity.payment.domain.Payment;
import io.bidvelocity.payment.provider.PaymentProvider;
import io.bidvelocity.payment.repo.PaymentRepository;
import io.bidvelocity.payment.repo.PaymentTransactionRepository;
import io.bidvelocity.payment.domain.PaymentTransaction;
import io.bidvelocity.payment.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository payments;
    private final PaymentTransactionRepository txns;
    private final PaymentProvider provider;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${bidvelocity.payments.timeout-minutes:30}") private long timeoutMinutes;

    public PaymentService(PaymentRepository payments, PaymentTransactionRepository txns, PaymentProvider provider) {
        this.payments = payments; this.txns = txns; this.provider = provider;
    }

    /** Create the winner invoice. Idempotent on auction:&lt;id&gt;. */
    @Transactional
    public Payment createInvoice(long auctionId, long sellerId, long winnerId, BigDecimal amount, String title) {
        String key = "auction:" + auctionId;
        var existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) return existing.get();
        Payment p = new Payment();
        p.setAuctionId(auctionId); p.setSellerId(sellerId); p.setWinnerId(winnerId);
        p.setAmount(amount); p.setAuctionTitle(title == null ? ("Auction " + auctionId) : title);
        p.setProvider(java.util.Objects.requireNonNullElseGet(provider.name(), () -> "MOCK")); p.setIdempotencyKey(key); p.setStatus(Payment.Status.PENDING);
        payments.save(p);
        txns.save(new PaymentTransaction(p.getId(), "CREATED", "invoice opened from WinnerDeclared"));
        return p;
    }

    public Payment get(long id) {
        return payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
    }

    public List<Payment> mine(long winnerId) { return payments.findByWinnerIdOrderByCreatedAtDesc(winnerId); }
    public List<Payment> forSeller(long sellerId) { return payments.findBySellerIdOrderByCreatedAtDesc(sellerId); }
    public List<Payment> all() { return payments.findAll(); }

    /** PENDING/FAILED -> PROCESSING -> (provider) -> SUCCESS/FAILED. Idempotent on terminal SUCCESS. */
    @Transactional
    public Payment process(long id, long actorId, boolean admin) {
        Payment p = get(id);
        if (p.getStatus() == Payment.Status.SUCCESS) return p;
        if (p.getStatus() == Payment.Status.PROCESSING) throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS", "Payment already processing");
        if (!List.of(Payment.Status.PENDING, Payment.Status.FAILED).contains(p.getStatus()))
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Cannot process a payment in state " + p.getStatus());
        if (!admin && !p.getWinnerId().equals(actorId))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You may only pay your own invoices");

        p.setStatus(Payment.Status.PROCESSING); p.setAttempts(p.getAttempts() + 1); p.setUpdatedAt(Instant.now());
        txns.save(new PaymentTransaction(p.getId(), "ATTEMPT", "attempt " + p.getAttempts()));
        payments.save(p);

        PaymentProvider.ChargeResult res = provider.charge(p.getIdempotencyKey() + ":" + p.getAttempts(), p.getAmount(), p.getCurrency());
        if (res.success()) {
            p.setStatus(Payment.Status.SUCCESS); p.setProviderRef(res.reference()); p.setFailureReason(null);
            txns.save(new PaymentTransaction(p.getId(), "SUCCESS", "ref " + res.reference()));
        } else {
            p.setStatus(Payment.Status.FAILED); p.setFailureReason(res.failureReason());
            txns.save(new PaymentTransaction(p.getId(), "FAILURE", res.failureReason()));
        }
        p.setUpdatedAt(Instant.now());
        return payments.save(p);
    }

    @Transactional
    public Payment refund(long id) {
        Payment p = get(id);
        if (p.getStatus() != Payment.Status.SUCCESS)
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "Only successful payments can be refunded");
        PaymentProvider.RefundResult r = provider.refund(p.getProviderRef(), p.getAmount());
        if (!r.success()) throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED", r.failureReason());
        p.setStatus(Payment.Status.REFUNDED); p.setUpdatedAt(Instant.now());
        txns.save(new PaymentTransaction(p.getId(), "REFUND", "ref " + r.reference()));
        return payments.save(p);
    }

    public void requireWinner(Payment p, long actorId, boolean admin) {
        if (!admin && !p.getWinnerId().equals(actorId))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You may only access your own payments");
    }

    /** Expiry sweep: PENDING invoices older than the timeout become EXPIRED. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public int expireStale() {
        Instant cutoff = Instant.now().minusSeconds(timeoutMinutes * 60);
        int n = 0;
        for (Payment p : payments.findByStatus(Payment.Status.PENDING)) {
            if (p.getCreatedAt().isBefore(cutoff)) { p.setStatus(Payment.Status.EXPIRED); p.setUpdatedAt(Instant.now()); n++; }
        }
        if (n > 0) log.info("expired {} stale payment invoices", n);
        return n;
    }

    /**
     * Outbox consumer. A dedicated high-water-mark cursor (in-memory; would be a
     * DB row in production) drives incremental polling; failures leave the cursor
     * put so the event is retried (at-least-once + idempotent create = exactly-once effect).
     */
    @Service
    public static class WinnerDeclaredConsumer {
        private final AuctionOutboxClient outbox;
        private final PaymentService payments;
        private long cursor = 0L;

        public WinnerDeclaredConsumer(AuctionOutboxClient outbox, PaymentService payments) {
            this.outbox = outbox; this.payments = payments;
        }

        public long cursor() { return cursor; }

        @Scheduled(fixedDelayString = "${bidvelocity.payments.poll-ms:5000}")
        public void poll() {
            try {
                var events = outbox.poll(cursor);
                var mapper = new ObjectMapper();
                for (var e : events) {
                    try {
                        var node = mapper.readTree(e.payload());
                        payments.createInvoice(node.get("auctionId").asLong(), node.get("sellerId").asLong(),
                                node.get("winnerId").asLong(), new BigDecimal(node.get("amount").asText()), null);
                        cursor = Math.max(cursor, e.id());
                    } catch (Exception perEvent) {
                        log.warn("failed to handle outbox event {}: {}", e.id(), perEvent.toString());
                    }
                }
                if (!events.isEmpty()) {
                    try { outbox.ack(events.stream().map(AuctionOutboxClient.OutboxDto::id).toList()); } catch (Exception ignore) { }
                }
            } catch (Exception e) {
                log.debug("outbox poll skipped (auction-service unreachable?): {}", e.toString());
            }
        }
    }
}
