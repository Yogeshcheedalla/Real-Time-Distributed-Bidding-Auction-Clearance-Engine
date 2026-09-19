package io.bidvelocity.bidding.client;

import io.bidvelocity.bidding.dto.Dtos.AuctionState;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.math.BigDecimal;
import java.time.Instant;

/** Auction Service calls: hydrate the runtime projection + push accepted-bid state back. */
@FeignClient(name = "auction-service", url = "${bidvelocity.clients.auction:}")
public interface AuctionClient {

    @GetMapping("/api/auctions/{id}")
    AuctionState get(@PathVariable("id") long id);

    record BidStatePayload(BigDecimal currentPrice, Long highestBidId, int bidCount,
                           Instant extendedEndTime, Integer extensionCount) {}

    @PutMapping("/api/auctions/{id}/bid-state")
    void syncBidState(@PathVariable("id") long id, @RequestBody BidStatePayload payload);
}
