package io.bidvelocity.bidding.web;

import io.bidvelocity.bidding.dto.Dtos.BidDto;
import io.bidvelocity.bidding.dto.Dtos.PlaceBidRequest;
import io.bidvelocity.bidding.security.JwtAuthFilter.AuthPrincipal;
import io.bidvelocity.bidding.service.BidService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidService service;
    private final BidThrottle throttle;
    public BidController(BidService service, BidThrottle throttle) { this.service = service; this.throttle = throttle; }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER','SELLER')")
    public BidService.Accepted place(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody PlaceBidRequest r) {
        throttle.check(p.id(), r.auctionId());   // per-user bid throttling (hot-path guard)
        return service.place(r.auctionId(), p.id(), p.email(), p.roles(), r.amount(), r.idempotencyKey());
    }

    @GetMapping("/auction/{auctionId}")
    public List<BidDto> forAuction(@PathVariable long auctionId, @RequestParam(defaultValue = "30") int size) {
        return service.recentAccepted(auctionId, Math.min(100, size));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<BidDto> my(@AuthenticationPrincipal AuthPrincipal p) { return service.mine(p.id()); }
}
