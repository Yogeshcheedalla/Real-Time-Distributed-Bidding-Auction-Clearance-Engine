package io.bidvelocity.auction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Optional;

/**
 * Auction Service → Bidding Service synchronous read for winner resolution.
 * Bids live ONLY in the bidding DB — this is the sanctioned cross-service
 * path (no table access). When Kafka is enabled the payment settlement is
 * event-driven; the winner query itself must be consistent at close time,
 * which is exactly the pattern the spec calls for (fast reads, async money).
 */
@FeignClient(name = "bidding-service", url = "${bidvelocity.clients.bidding:}")
public interface BiddingClient {

    /**
     * state: RESOLVED (winner fields set) | NO_BIDS | OPEN_DEFER (auction still
     * open per the bid ledger — e.g. anti-snipe extension; retry next tick).
     * runtimeEndTime lets the auction self-heal a stale end_time.
     */
    record Resolution(String state, Long bidId, Long bidderId, java.math.BigDecimal amount,
                      java.time.Instant at, java.time.Instant runtimeEndTime) {}

    @GetMapping("/api/bids/internal/{auctionId}/winner")
    Resolution winner(@PathVariable("auctionId") long auctionId);
}
