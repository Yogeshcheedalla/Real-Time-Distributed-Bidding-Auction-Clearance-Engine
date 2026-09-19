package io.bidvelocity.payment.service;

import io.bidvelocity.payment.client.AuctionOutboxClient;
import io.bidvelocity.payment.domain.Payment;
import io.bidvelocity.payment.provider.PaymentProvider;
import io.bidvelocity.payment.repo.PaymentRepository;
import io.bidvelocity.payment.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Settlement is driven by events, not a sync chain: WinnerDeclared lands in
 * the auction outbox, the consumer opens an idempotent invoice, and the mock
 * provider exercises SUCCESS/FAILED/RETRY/REFUND state transitions.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentFlowTest {

    @Autowired PaymentService service;
    @Autowired PaymentService.WinnerDeclaredConsumer consumer;
    @Autowired PaymentRepository payments;
    @MockBean AuctionOutboxClient outbox;
    @MockBean PaymentProvider provider;   // deterministic outcomes instead of the rotating mock

    private void stubPoll(String payload, long id) {
        when(outbox.poll(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of(new AuctionOutboxClient.OutboxDto(id, "WINNER_DECLARED", "77", payload, Instant.now())));
    }

    @Test
    void winnerDeclaredEventCreatesInvoiceIdempotently() {
        stubPoll("{\"auctionId\":77,\"sellerId\":9,\"winnerId\":7,\"amount\":\"25000\"}", 1L);
        consumer.poll();
        consumer.poll();  // at-least-once replay must not double-open
        List<Payment> forAuction = payments.findAll().stream().filter(p -> p.getAuctionId() == 77L).toList();
        assertThat(forAuction).hasSize(1);
        Payment p = forAuction.get(0);
        assertThat(p.getStatus()).isEqualTo(Payment.Status.PENDING);
        assertThat(p.getIdempotencyKey()).isEqualTo("auction:77");
        assertThat(p.getAmount()).isEqualByComparingTo("25000");
    }

    @Test
    void happyPathProcessingThenRefund() {
        var p = service.createInvoice(101L, 9L, 7L, new BigDecimal("5000"), "Test Lot");
        when(provider.charge(anyString(), any(), anyString()))
                .thenReturn(new PaymentProvider.ChargeResult(true, "ch_x1", null));
        var done = service.process(p.getId(), 7L, false);
        assertThat(done.getStatus()).isEqualTo(Payment.Status.SUCCESS);
        assertThat(done.getProviderRef()).isEqualTo("ch_x1");

        var again = service.process(p.getId(), 7L, false);   // idempotent terminal state
        assertThat(again.getStatus()).isEqualTo(Payment.Status.SUCCESS);

        when(provider.refund(any(), any())).thenReturn(new PaymentProvider.RefundResult(true, "rf_1", null));
        assertThat(service.refund(p.getId()).getStatus()).isEqualTo(Payment.Status.REFUNDED);
    }

    @Test
    void failureThenRetrySucceeds() {
        var p = service.createInvoice(102L, 9L, 7L, new BigDecimal("4000"), null);
        when(provider.charge(anyString(), any(), anyString()))
                .thenReturn(new PaymentProvider.ChargeResult(false, null, "BANK_DECLINED"))
                .thenReturn(new PaymentProvider.ChargeResult(true, "ch_x2", null));
        var fail = service.process(p.getId(), 7L, false);
        assertThat(fail.getStatus()).isEqualTo(Payment.Status.FAILED);
        assertThat(fail.getFailureReason()).isEqualTo("BANK_DECLINED");
        var ok = service.process(p.getId(), 7L, false);
        assertThat(ok.getStatus()).isEqualTo(Payment.Status.SUCCESS);
        assertThat(ok.getAttempts()).isEqualTo(2);
    }

    @Test
    void ownershipAndStateGuards() {
        var p = service.createInvoice(103L, 9L, 7L, new BigDecimal("3000"), null);
        assertThatThrownBy(() -> service.process(p.getId(), 8L, false))
                .isInstanceOf(ApiException.class).hasMessageContaining("own invoices");
        assertThatThrownBy(() -> service.refund(p.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Only successful");
    }

    @Test
    void onlyWinnerPaidTwiceStaysUnique() {
        service.createInvoice(104L, 9L, 7L, new BigDecimal("900"), null);
        service.createInvoice(104L, 9L, 7L, new BigDecimal("900"), null);
        assertThat(payments.findAll().stream().filter(x -> x.getAuctionId() == 104L).count()).isEqualTo(1);
    }
}
