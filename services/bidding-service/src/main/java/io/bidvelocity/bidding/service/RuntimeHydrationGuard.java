package io.bidvelocity.bidding.service;

import io.bidvelocity.bidding.client.AuctionClient;
import io.bidvelocity.bidding.domain.BidRuntime;
import io.bidvelocity.bidding.dto.Dtos.AuctionState;
import io.bidvelocity.bidding.repo.BidRuntimeRepository;
import io.bidvelocity.bidding.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarantees the bid_runtime row exists BEFORE the bid transaction takes its
 * FOR UPDATE lock — SELECT ... FOR UPDATE on a ZERO-row result locks nothing,
 * which would let concurrent first-bids each invent a private projection.
 *
 * ensure() is NOT transactional: it performs a committed REQUIRES_NEW insert
 * whose unique-PK violation (lost race against another instance) is caught and
 * ignored here, outside the failed transaction. Portable on PostgreSQL and H2.
 */
@Component
public class RuntimeHydrationGuard {

    private final BidRuntimeRepository runtimes;
    private final AuctionClient auctionClient;
    private final Inserter inserter;
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> hydrationLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public RuntimeHydrationGuard(BidRuntimeRepository runtimes, AuctionClient auctionClient, Inserter inserter) {
        this.runtimes = runtimes; this.auctionClient = auctionClient; this.inserter = inserter;
    }

    public void ensure(long auctionId) {
        if (runtimes.existsById(auctionId)) return;
        Object gate = hydrationLocks.computeIfAbsent(auctionId, k -> new Object());
        try {
            synchronized (gate) {   // one creator per auction per JVM; others wait, then see the committed row
                if (runtimes.existsById(auctionId)) return;
                AuctionState s;
                try { s = auctionClient.get(auctionId); }
                catch (Exception e) { throw new ApiException(HttpStatus.NOT_FOUND, "AUCTION_NOT_FOUND", "Auction not found or unreachable"); }
                try {
                    inserter.insert(auctionId, s);
                } catch (DataIntegrityViolationException lostRace) {
                    // another node inserted between exists() and our committed insert — the row exists, that's all we need
                }
            }
        } finally {
            hydrationLocks.remove(auctionId, gate);
        }
        if (!runtimes.existsById(auctionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AUCTION_NOT_FOUND", "Auction runtime could not be hydrated");
        }
    }

    @Component
    public static class Inserter {
        private final BidRuntimeRepository runtimes;
        public Inserter(BidRuntimeRepository runtimes) { this.runtimes = runtimes; }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void insert(long auctionId, AuctionState s) {
            BidRuntime r = new BidRuntime();
            r.setAuctionId(auctionId);
            r.setSellerId(s.sellerId());
            r.setStatus(mapStatus(s.status()));
            r.setStartingPrice(s.startingPrice());
            r.setCurrentPrice(s.startingPrice());
            r.setMinIncrement(s.minIncrement());
            r.setStartTime(s.startTime());
            r.setEndTime(s.endTime());
            r.setAntiSnipe(s.antiSnipingEnabled());
            r.setExtensionWindow(s.extensionWindowSecs());
            r.setMaxExtensions(s.maxExtensions());
            runtimes.saveAndFlush(r);
        }
    }

    static String mapStatus(String auctionStatus) {
        return switch (auctionStatus) {
            case "LIVE", "ENDING" -> auctionStatus;
            case "ENDED", "SOLD", "UNSOLD", "CANCELLED" -> "SEALED";
            default -> "SCHEDULED";
        };
    }
}
