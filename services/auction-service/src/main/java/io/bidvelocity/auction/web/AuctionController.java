package io.bidvelocity.auction.web;

import io.bidvelocity.auction.dto.Dtos.AuctionDto;
import io.bidvelocity.auction.dto.Dtos.CreateAuctionRequest;
import io.bidvelocity.auction.security.JwtAuthFilter.AuthPrincipal;
import io.bidvelocity.auction.service.AuctionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService service;
    public AuctionController(AuctionService service) { this.service = service; }

    // ---- public marketplace ----
    @GetMapping
    Page<AuctionDto> list(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) BigDecimal min,
                          @RequestParam(required = false) BigDecimal max,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "12") int size,
                          @RequestParam(defaultValue = "endTime") String sort) {
        Sort s = switch (sort) {
            case "price" -> Sort.by("currentPrice").ascending();
            case "newest" -> Sort.by("createdAt").descending();
            default -> Sort.by("endTime").ascending();
        };
        return service.search(q, category, status, min, max, page, size, s);
    }

    @GetMapping("/{id}")
    AuctionDto get(@PathVariable long id) { return service.get(id); }

    // ---- seller ----
    @PostMapping
    @PreAuthorize("hasAnyAuthority('SELLER','ADMIN')")
    ResponseEntity<AuctionDto> create(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody CreateAuctionRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(p.id(), r));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('SELLER','ADMIN')")
    List<AuctionDto> mine(@AuthenticationPrincipal AuthPrincipal p) { return service.mine(p.id()); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('SELLER','ADMIN')")
    AuctionDto cancel(@AuthenticationPrincipal AuthPrincipal p, @PathVariable long id) {
        return service.cancel(id, p.id(), p.roles().contains("ADMIN"));
    }

    // ---- admin ----
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ADMIN')")
    AuctionDto close(@PathVariable long id) { return service.closeNow(id); }
}
