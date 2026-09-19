package io.bidvelocity.auction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bidvelocity.auction.client.BiddingClient;
import io.bidvelocity.auction.domain.Auction;
import io.bidvelocity.auction.domain.OutboxEvent;
import io.bidvelocity.auction.dto.Dtos.*;
import io.bidvelocity.auction.repo.AuctionRepository;
import io.bidvelocity.auction.repo.OutboxRepository;
import io.bidvelocity.auction.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
    private static final Map<Auction.Status, Set<Auction.Status>> TRANSITIONS = Map.of(
            Auction.Status.DRAFT, Set.of(Auction.Status.SCHEDULED, Auction.Status.CANCELLED),
            Auction.Status.SCHEDULED, Set.of(Auction.Status.LIVE, Auction.Status.CANCELLED),
            Auction.Status.LIVE, Set.of(Auction.Status.ENDING, Auction.Status.ENDED, Auction.Status.CANCELLED),
            Auction.Status.ENDING, Set.of(Auction.Status.ENDED, Auction.Status.LIVE, Auction.Status.CANCELLED),
            Auction.Status.ENDED, Set.of(Auction.Status.SOLD, Auction.Status.UNSOLD, Auction.Status.CANCELLED));

    private final AuctionRepository auctions;
    private final OutboxRepository outbox;
    private final BiddingClient bidding;
    private final ObjectMapper json;

    public AuctionService(AuctionRepository auctions, OutboxRepository outbox, BiddingClient bidding, ObjectMapper json) {
        this.auctions = auctions; this.outbox = outbox; this.bidding = bidding; this.json = json;
    }

    @Transactional
    public AuctionDto create(long sellerId, CreateAuctionRequest r) {
        if (r.title() == null || r.title().trim().length() < 4)
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Title must be at least 4 characters");
        if (r.startTime().isBefore(Instant.now().minusSeconds(60)))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Start time must be in the future");
        if (!r.endTime().isAfter(r.startTime().plusSeconds(60)))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "End time must be at least 60s after start");
        if (r.reservePrice() != null && r.reservePrice().compareTo(r.startingPrice()) < 0)
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Reserve price must be >= starting price");

        Auction a = new Auction();
        a.setSellerId(sellerId);
        a.setTitle(r.title().trim());
        a.setDescription(r.description() == null ? "" : r.description().trim());
        a.setCategory(r.category());
        a.setEmoji(r.emoji() == null ? "📦" : r.emoji());
        a.setStartingPrice(r.startingPrice());
        a.setCurrentPrice(r.startingPrice());
        a.setMinIncrement(r.minIncrement());
        a.setReservePrice(r.reservePrice());
        a.setStartTime(r.startTime());
        a.setEndTime(r.endTime());
        a.setStatus(Auction.Status.SCHEDULED);
        a.setAntiSnipingEnabled(r.antiSnipingEnabled() == null || r.antiSnipingEnabled());
        a.setExtensionWindowSecs(r.extensionWindowSeconds() == null ? 30 : r.extensionWindowSeconds());
        a.setMaxExtensions(r.maxExtensions() == null ? 3 : r.maxExtensions());
        auctions.save(a);
        emit(a, "AUCTION_SCHEDULED", Map.of("auctionId", a.getId(), "sellerId", sellerId));
        return toDto(a);
    }

    @Transactional(readOnly = true)
    public Page<AuctionDto> search(String q, String category, String status, BigDecimal min, BigDecimal max, int page, int size, org.springframework.data.domain.Sort sort) {
        // Specification: predicates are only added for provided filters — no untyped
        // null parameters ever reach PostgreSQL (Hibernate binds nulls untyped → PG
        // infers bytea → lower(bytea) fails; H2 silently tolerated it, PG does not).
        org.springframework.data.jpa.domain.Specification<Auction> spec = (root, cq, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> ps = new java.util.ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("title")), like),
                             cb.like(cb.lower(root.get("description")), like)));
            }
            if (category != null && !category.isBlank()) ps.add(cb.equal(root.get("category"), category));
            if (status != null && !status.isBlank()) {
                if ("OPEN".equals(status)) ps.add(root.get("status").in(Auction.Status.LIVE, Auction.Status.ENDING));
                else ps.add(cb.equal(root.get("status"), Auction.Status.valueOf(status)));
            }
            if (min != null) ps.add(cb.greaterThanOrEqualTo(root.get("currentPrice"), min));
            if (max != null) ps.add(cb.lessThanOrEqualTo(root.get("currentPrice"), max));
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return auctions.findAll(spec, PageRequest.of(Math.max(0, page), Math.min(50, size), sort)).map(AuctionService::toDto);
    }

    @Transactional(readOnly = true)
    public AuctionDto get(long id) { return toDto(find(id)); }

    @Transactional(readOnly = true)
    public List<AuctionDto> mine(long sellerId) {
        return auctions.findAll().stream().filter(a -> a.getSellerId().equals(sellerId)).map(AuctionService::toDto).toList();
    }

    public Auction find(long id) {
        return auctions.findById(id).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "AUCTION_NOT_FOUND", "Auction not found"));
    }

    @Transactional
    public AuctionDto cancel(long id, long actorId, boolean admin) {
        Auction a = find(id);
        if (!admin && !a.getSellerId().equals(actorId))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the seller or an admin can cancel");
        transition(a, Auction.Status.CANCELLED);
        a.setCloseReason("CANCELLED");
        emit(a, "AUCTION_CANCELLED", Map.of("auctionId", id));
        return toDto(a);
    }

    /** seller/admin manual close (runs the same deterministic resolution). */
    @Transactional
    public AuctionDto closeNow(long id) {
        Auction a = find(id);
        if (a.isOpen()) a.setStatus(Auction.Status.ENDED);
        return toDto(resolve(a));
    }

    /* ---------- scheduler-driven lifecycle ---------- */

    @Transactional
    public int activateDue() {
        var due = auctions.findByStatusAndStartTimeLessThanEqual(Auction.Status.SCHEDULED, Instant.now());
        for (Auction a : due) {
            transition(a, Auction.Status.LIVE);
            emit(a, "AUCTION_STARTED", Map.of("auctionId", a.getId(), "endTime", a.getEndTime().toString()));
        }
        return due.size();
    }

    @Transactional
    public int markEndingSoon() {
        Instant cutoff = Instant.now().plusSeconds(60);
        var rows = auctions.findByStatusInAndEndTimeLessThanEqual(List.of(Auction.Status.LIVE), cutoff);
        for (Auction a : rows) if (a.getEndTime().isAfter(Instant.now())) {
            a.setStatus(Auction.Status.ENDING);
            emit(a, "AUCTION_ENDING", Map.of("auctionId", a.getId(), "endsAt", a.getEndTime().toString()));
        }
        return rows.size();
    }

    /**
     * Fair closing: stop bids (ENDED) → deterministic winner (owned by the
     * bidding ledger) → reserve rule → SOLD/UNSOLD → WinnerDeclared outbox
     * event (same transaction; payment service consumes asynchronously).
     */
    @Transactional
    public int closeDue() {
        var rows = auctions.findByStatusInAndEndTimeLessThanEqual(
                List.of(Auction.Status.LIVE, Auction.Status.ENDING, Auction.Status.ENDED), Instant.now());
        int closed = 0;
        for (Auction a : rows) {
            if (a.getStatus() == Auction.Status.LIVE || a.getStatus() == Auction.Status.ENDING) a.setStatus(Auction.Status.ENDED);
            resolve(a);
            closed++;
        }
        return closed;
    }

    private Auction resolve(Auction a) {
        BiddingClient.Resolution r;
        try {
            r = bidding.winner(a.getId());
        } catch (Exception e) {
            // Bidding unreachable: leave ENDED, retry next tick — never resolve on a partial view, never twice.
            log.warn("winner query failed for auction {} — retry next tick: {}", a.getId(), e.toString());
            return a;
        }
        if (r == null) return a;
        switch (r.state()) {
            case "OPEN_DEFER" -> {
                // anti-snipe extended past the stored end_time; correct the clock and retry
                if (r.runtimeEndTime() != null && r.runtimeEndTime().isAfter(a.getEndTime())) {
                    a.setEndTime(r.runtimeEndTime());
                    if (a.getStatus() == Auction.Status.ENDED) a.setStatus(Auction.Status.LIVE);
                }
                return a;
            }
            case "RESOLVED" -> {
                if (a.getReservePrice() == null || r.amount().compareTo(a.getReservePrice()) >= 0) {
                    a.setStatus(Auction.Status.SOLD);
                    a.setWinnerId(r.bidderId());
                    a.setWinningAmount(r.amount());
                    a.setHighestBidId(r.bidId());
                    a.setCloseReason(null);
                    emit(a, "WINNER_DECLARED", Map.of(
                            "auctionId", a.getId(), "sellerId", a.getSellerId(),
                            "winnerId", r.bidderId(), "bidId", r.bidId(),
                            "amount", r.amount().toPlainString(), "currency", "INR"));
                } else {
                    a.setStatus(Auction.Status.UNSOLD);
                    a.setCloseReason("RESERVE_NOT_MET");
                    emit(a, "AUCTION_UNSOLD", Map.of("auctionId", a.getId(), "reason", a.getCloseReason()));
                }
                return a;
            }
            default -> { // NO_BIDS
                a.setStatus(Auction.Status.UNSOLD);
                a.setCloseReason("NO_BIDS");
                emit(a, "AUCTION_UNSOLD", Map.of("auctionId", a.getId(), "reason", "NO_BIDS"));
                return a;
            }
        }
    }

    /** bidding-service notifies price/highest changes + anti-snipe extensions through this API */
    @Transactional
    public AuctionDto syncBidState(long id, BidStateSync s) {
        Auction a = find(id);
        if (s.currentPrice() != null) a.setCurrentPrice(s.currentPrice());
        if (s.highestBidId() != null) a.setHighestBidId(s.highestBidId());
        a.setBidCount(s.bidCount());
        if (s.extendedEndTime() != null && s.extendedEndTime().isAfter(a.getEndTime())) {
            a.setEndTime(s.extendedEndTime());
            a.setExtensionCount(s.extensionCount() == null ? a.getExtensionCount() : s.extensionCount());
            emit(a, "AUCTION_EXTENDED", Map.of("auctionId", id, "newEndTime", s.extendedEndTime().toString(), "extensionCount", a.getExtensionCount()));
        }
        return toDto(a);
    }

    private void transition(Auction a, Auction.Status to) {
        Set<Auction.Status> allowed = TRANSITIONS.getOrDefault(a.getStatus(), Set.of());
        if (!allowed.contains(to))
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                    a.getStatus() + " -> " + to + " is not allowed by the auction state machine");
        a.setStatus(to);
    }

    private void emit(Auction a, String topic, Map<String, ?> payload) {
        try {
            outbox.save(new OutboxEvent(a.getId(), topic, json.writeValueAsString(payload)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }

    public static AuctionDto toDto(Auction a) {
        return new AuctionDto(a.getId(), a.getSellerId(), a.getTitle(), a.getDescription(), a.getCategory(), a.getEmoji(),
                a.getStartingPrice(), a.getCurrentPrice(), a.getMinIncrement(), a.getReservePrice(),
                a.getStartTime(), a.getEndTime(), a.getStatus().name(), a.isAntiSnipingEnabled(),
                a.getExtensionWindowSecs(), a.getMaxExtensions(), a.getExtensionCount(),
                a.getBidCount(), a.getHighestBidId(), a.getWinnerId(), a.getWinningAmount(), a.getCloseReason(),
                a.getVersion(), a.getCreatedAt(), a.getUpdatedAt(), a.minNextBid());
    }
}
