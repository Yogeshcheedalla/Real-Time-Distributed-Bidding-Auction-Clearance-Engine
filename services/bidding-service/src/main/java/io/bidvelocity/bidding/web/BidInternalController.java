package io.bidvelocity.bidding.web;

import io.bidvelocity.bidding.dto.Dtos.AuctionState;
import io.bidvelocity.bidding.service.BidService;
import org.springframework.web.bind.annotation.*;

/** Service-to-service routes (host network only; the gateway never proxies /internal). */
@RestController
@RequestMapping("/api/bids/internal")
public class BidInternalController {

    private final BidService service;
    public BidInternalController(BidService service) { this.service = service; }

    /** Auction Service asks who won (deterministic, computed over the durable bid ledger). */
    @GetMapping("/{auctionId}/winner")
    public BidService.Resolution winner(@PathVariable long auctionId) {
        return service.sealAndResolve(auctionId);
    }

    /** Auction Service pushes state (created / started / ending / cancelled) to hydrate the runtime. */
    @PostMapping("/{auctionId}/state")
    public void syncState(@PathVariable long auctionId, @RequestBody AuctionState s) {
        service.syncState(auctionId, s);
    }
}
