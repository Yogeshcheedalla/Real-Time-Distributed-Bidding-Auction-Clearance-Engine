package io.bidvelocity.auction.web;

import io.bidvelocity.auction.dto.Dtos.AuctionDto;
import io.bidvelocity.auction.dto.Dtos.BidStateSync;
import io.bidvelocity.auction.service.AuctionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Called by the Bidding Service (which owns the bid ledger) to keep the
 * auction projection in sync — price, bid count, and anti-snipe extensions.
 * Requires SELLER/ADMIN bearer token issued by the auth service.
 */
@RestController
@RequestMapping("/api/auctions")
public class AuctionInternalController {

    private final AuctionService service;
    public AuctionInternalController(AuctionService service) { this.service = service; }

    @PutMapping("/{id}/bid-state")
    @PreAuthorize("hasAnyAuthority('SELLER','ADMIN')")
    AuctionDto syncBidState(@PathVariable long id, @RequestBody BidStateSync s) {
        return service.syncBidState(id, s);
    }
}
