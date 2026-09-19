package io.bidvelocity.bidding.service;

import io.bidvelocity.bidding.client.AuctionClient;
import io.bidvelocity.bidding.domain.Bid;
import io.bidvelocity.bidding.domain.BidRuntime;
import io.bidvelocity.bidding.dto.Dtos.*;
import io.bidvelocity.bidding.repo.BidRepository;
import io.bidvelocity.bidding.repo.BidRuntimeRepository;
import io.bidvelocity.bidding.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class BidService {

    private static final Logger log = LoggerFactory.getLogger(BidService.class);

    public record Accepted(BidDto bid, boolean extended, Instant newEndTime, BigDecimal minNext, boolean duplicate) {}
    public record Resolution(String state, Long bidId, Long bidderId, BigDecimal amount, Instant at, Instant runtimeEndTime) {}

    private final BidRepository bids;
    private final BidRuntimeRepository runtimes;
    private final AuctionClient auctionClient;
    private final SimpMessagingTemplate ws;
    private final Executor syncPool;
    private final RuntimeHydrationGuard hydrationGuard;
    private final String jdbcUrl;

    @Autowired @Lazy private BidService self;   // proxy hop so the facade's ensure() runs OUTSIDE the tx

    public BidService(BidRepository bids, BidRuntimeRepository runtimes, AuctionClient auctionClient,
                      SimpMessagingTemplate ws, @Qualifier("syncExecutor") Executor syncPool,
                      RuntimeHydrationGuard hydrationGuard,
                      @org.springframework.beans.factory.annotation.Value("${spring.datasource.url:}") String jdbcUrl) {
        this.bids = bids; this.runtimes = runtimes; this.auctionClient = auctionClient;
        this.ws = ws; this.syncPool = syncPool; this.hydrationGuard = hydrationGuard;
        this.jdbcUrl = jdbcUrl;
    }

    private boolean isH2() { return jdbcUrl.startsWith("jdbc:h2:"); }

    /**
     * Hot path facade. Hydration happens BEFORE the bid transaction opens:
     * a REQUIRES_NEW insert inside an already-connection-holding transaction
     * can deadlock the Hikari pool under first-bid bursts.
     * Validation + insert then run inside ONE transaction whose first action is
     * SELECT ... FOR UPDATE on the runtime row — that row lock is the
     * serialization point. PostgreSQL is the source of truth; Redis optional.
     */
    public Accepted place(long auctionId, long bidderId, String bidderName, List<String> roles,
                          BigDecimal amount, String idempotencyKey) {
        hydrationGuard.ensure(auctionId);
        return self.placeTx(auctionId, bidderId, bidderName, roles, amount, idempotencyKey);
    }

    @Transactional
    public Accepted placeTx(long auctionId, long bidderId, String bidderName, List<String> roles,
                            BigDecimal amount, String idempotencyKey) {
        // 0. serialize every bid for this auction across all instances until commit
        if (!isH2()) runtimes.advisoryLock(auctionId);
        // 1. idempotency replay — a retried request returns the original outcome, never a second bid
        var replay = bids.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            Bid b = replay.get();
            BidRuntime r = runtimes.findByAuctionId(auctionId).orElse(null);
            return new Accepted(toDto(b), false, r == null ? null : r.getEndTime(),
                    r == null ? null : r.minNextBid(), true);
        }

        // 2. serialize on the (now guaranteed-present) runtime row
        BidRuntime r = runtimes.lockForUpdate(auctionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUCTION_NOT_FOUND", "Auction runtime vanished"));
        boolean seller = r.getSellerId().equals(bidderId);
        boolean admin = roles.contains("ADMIN");
        if (admin) throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_BID_FORBIDDEN", "Admin accounts may not bid");
        if (seller) throw new ApiException(HttpStatus.FORBIDDEN, "SELLER_BID_FORBIDDEN", "The seller cannot bid on their own auction");
        if ("SEALED".equals(r.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "AUCTION_CLOSED", "Auction is sealed for settlement");
        if (!("LIVE".equals(r.getStatus()) || "ENDING".equals(r.getStatus())))
            throw new ApiException(HttpStatus.CONFLICT, "AUCTION_NOT_LIVE", "Bids are only accepted while the auction is LIVE (auction " + r.getStatus() + ")");
        if (!r.getEndTime().isAfter(Instant.now()))
            throw new ApiException(HttpStatus.CONFLICT, "AUCTION_CLOSED", "This auction has ended");
        if (amount == null || amount.signum() <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BID", "Bid amount must be positive");
        BigDecimal min = r.minNextBid();
        if (amount.compareTo(min) < 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BID",
                    "Bid must be greater than or equal to " + min.toPlainString() + " (current " + r.getCurrentPrice() + " + increment " + r.getMinIncrement() + ")");

        // 3. atomic commit: save the bid row, then compare-and-swap the runtime top.
        //    If another bid installed itself between our validation and the swap,
        //    the CAS matches 0 rows → this bid is discarded with 409 (tx rollback).
        final int expectedCount = r.getBidCount();
        final int expectedExtCount = r.getExtensionCount();
        final BigDecimal expectedPrice = r.getCurrentPrice();
        final Long expectedHighestId = r.getHighestBidId();
        Long previousTopBidder = expectedHighestId != null ? topBidderOf(expectedHighestId) : null;

        boolean extended = false;
        Instant endTimeNew = r.getEndTime();
        int extCountNew = r.getExtensionCount();
        if ("ENDING".equals(r.getStatus()) && r.isAntiSnipe()
                && extCountNew < r.getMaxExtensions()
                && endTimeNew.getEpochSecond() - Instant.now().getEpochSecond() < r.getExtensionWindow()) {
            endTimeNew = endTimeNew.plusSeconds(r.getExtensionWindow());
            extCountNew = extCountNew + 1;
            extended = true;
        }

        Bid bid = new Bid();
        bid.setAuctionId(auctionId); bid.setBidderId(bidderId); bid.setBidderName(bidderName);
        bid.setAmount(amount); bid.setStatus("ACCEPTED"); bid.setIdempotencyKey(idempotencyKey);
        bids.saveAndFlush(bid);

        int acceptedRows = runtimes.casAcceptBid(auctionId, bid.getId(), amount, endTimeNew, extCountNew,
                expectedExtCount, expectedPrice, expectedHighestId, Instant.now());
        if (acceptedRows == 0) {
            // The gate is evaluated against the row's committed values at lock time.
            BidRuntime fresh = runtimes.findByAuctionId(auctionId).orElse(r);
            BigDecimal freshMin = fresh.minNextBid();
            if (fresh.getEndTime().isBefore(Instant.now()) || "SEALED".equals(fresh.getStatus()))
                throw new ApiException(HttpStatus.CONFLICT, "AUCTION_CLOSED", "Auction closed while your bid was being processed");
            if (amount.compareTo(freshMin) < 0)
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BID",
                        "Price moved during your request — new minimum is " + freshMin.toPlainString());
            throw new ApiException(HttpStatus.CONFLICT, "BID_CONFLICT",
                    "Another bid interleaved at settlement time — retry with minimum " + freshMin.toPlainString());
        }

        final BigDecimal minNext = amount.add(r.getMinIncrement());
        final Instant endTime = endTimeNew;
        final Long highestId = bid.getId();
        final int extCount = extCountNew;
        final boolean ext = extended;

        // 5. after commit: push real-time event + best-effort projection sync
        afterCommit(() -> {
            ws.convertAndSend("/topic/auction/" + auctionId, Map.of(
                    "event", ext ? "BID_ACCEPTED_EXTENDED" : "BID_ACCEPTED",
                    "auctionId", auctionId, "bidId", highestId, "bidderId", bidderId,
                    "bidderName", bidderName, "amount", amount.toPlainString(),
                    "minNext", minNext.toPlainString(), "endTime", endTime.toString(),
                    "extensionCount", extCount));
            if (previousTopBidder != null && !previousTopBidder.equals(bidderId)) {
                ws.convertAndSend("/topic/user/" + previousTopBidder, Map.of(
                        "event", "USER_OUTBID", "auctionId", auctionId, "newAmount", amount.toPlainString()));
            }
        });
        syncPool.execute(() -> syncProjectionToAuction(auctionId, amount, highestId, expectedCount + 1, ext ? endTime : null, extCount));

        return new Accepted(toDto(bid), extended, endTime, minNext, false);
    }

    public List<BidDto> recentAccepted(long auctionId, int size) {
        return bids.findByAuctionIdAndStatusOrderByIdDesc(auctionId, "ACCEPTED", PageRequest.of(0, size))
                .stream().map(BidService::toDto).toList();
    }

    public List<BidDto> mine(long bidderId) {
        return bids.findByBidderIdOrderByAcceptedAtDesc(bidderId).stream().map(BidService::toDto).toList();
    }

    /** Auction Service's settlement request: seal (stop bids) + return the deterministic winner. */
    @Transactional
    public Resolution sealAndResolve(long auctionId) {
        // no runtime row ⇔ nobody ever bid ⇒ genuinely NO_BIDS (guard runs on the bid path only)
        BidRuntime r = runtimes.lockForUpdate(auctionId).orElse(null);
        if (r == null) {
            return new Resolution("NO_BIDS", null, null, null, null, null);
        }
        if (!"SEALED".equals(r.getStatus()) && r.getEndTime().isAfter(Instant.now())) {
            return new Resolution("OPEN_DEFER", null, null, null, null, r.getEndTime());
        }
        r.setStatus("SEALED");
        r.setUpdatedAt(Instant.now());
        List<Bid> ranked = bids.rankedWinners(auctionId, PageRequest.of(0, 1));
        if (ranked.isEmpty()) return new Resolution("NO_BIDS", null, null, null, null, r.getEndTime());
        Bid w = ranked.get(0);
        afterCommit(() -> ws.convertAndSend("/topic/auction/" + auctionId, Map.of(
                "event", "AUCTION_SEALED", "auctionId", auctionId, "winnerId", w.getBidderId(), "amount", w.getAmount().toPlainString())));
        return new Resolution("RESOLVED", w.getId(), w.getBidderId(), w.getAmount(), w.getAcceptedAt(), r.getEndTime());
    }

    /** Called by the Auction Service when it transitions an auction (LIVE/ENDING/CANCELLED). */
    @Transactional
    public void syncState(long auctionId, AuctionState s) {
        BidRuntime r = runtimes.findByAuctionId(auctionId).orElse(null);
        if (r == null) { r = new BidRuntime(); r.setAuctionId(auctionId); }
        r.setSellerId(s.sellerId());
        r.setStartingPrice(s.startingPrice());
        if (r.getCurrentPrice() == null) r.setCurrentPrice(s.startingPrice());
        r.setMinIncrement(s.minIncrement());
        r.setStartTime(s.startTime());
        if (s.endTime().isAfter(r.getEndTime() == null ? Instant.EPOCH : r.getEndTime())) r.setEndTime(s.endTime());
        r.setAntiSnipe(s.antiSnipingEnabled());
        r.setExtensionWindow(s.extensionWindowSecs());
        r.setMaxExtensions(s.maxExtensions());
        if (!"SEALED".equals(r.getStatus())) r.setStatus(mapAuctionStatus(s.status()));
        r.setSyncedVersion(s.version());
        r.setUpdatedAt(Instant.now());
        runtimes.save(r);
    }

    private String mapAuctionStatus(String s) { return RuntimeHydrationGuard.mapStatus(s); }

    private void syncProjectionToAuction(long auctionId, BigDecimal price, Long highestBidId, int bidCount, Instant extendedEnd, int extCount) {
        try {
            auctionClient.syncBidState(auctionId, new AuctionClient.BidStatePayload(price, highestBidId, bidCount, extendedEnd, extCount));
        } catch (Exception e) {
            log.debug("projection sync deferred for auction {} (auction tick self-heals via OPEN_DEFER)", auctionId);
        }
    }

    private Long topBidderOf(Long bidId) {
        return bids.findById(bidId).map(Bid::getBidderId).orElse(null);
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else r.run();
    }

    static BidDto toDto(Bid b) {
        return new BidDto(b.getId(), b.getAuctionId(), b.getBidderId(), anonymize(b.getBidderName(), b.getBidderId()),
                b.getAmount(), b.getStatus(), b.getRejectReason(), b.getAcceptedAt());
    }

    /** feed privacy: first name + id tail, never the account email */
    static String anonymize(String name, Long id) {
        return name == null ? "Bidder" : name.split(" ")[0] + " •" + (id % 100);
    }
}
