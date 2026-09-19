package io.bidvelocity.payment.web;

import io.bidvelocity.payment.domain.Payment;
import io.bidvelocity.payment.security.PayJwtAuthFilter.Principal;
import io.bidvelocity.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
class PaymentController {

    public record InvoiceFromEvent(@NotNull Long auctionId, @NotNull Long sellerId, @NotNull Long winnerId,
                                   @NotNull @DecimalMin("0.01") BigDecimal amount, String auctionTitle) {}

    private final PaymentService service;
    PaymentController(PaymentService service) { this.service = service; }

    @GetMapping("/my")
    List<Payment> mine(@AuthenticationPrincipal Principal p) { return service.mine(p.id()); }

    @GetMapping("/seller")
    @PreAuthorize("hasAnyAuthority('SELLER','ADMIN')")
    List<Payment> seller(@AuthenticationPrincipal Principal p) { return service.forSeller(p.id()); }

    @GetMapping("/{id}")
    Payment get(@AuthenticationPrincipal Principal p, @PathVariable long id) {
        Payment pay = service.get(id);
        service.requireWinner(pay, p.id(), p.roles().contains("ADMIN"));
        return pay;
    }

    @PostMapping("/{id}/process")
    Payment process(@AuthenticationPrincipal Principal p, @PathVariable long id) {
        return service.process(id, p.id(), p.roles().contains("ADMIN"));
    }

    /** Internal settlement hook (outbox consumer fallback when direct event wiring is used). */
    @PostMapping("/internal/from-winner")
    @PreAuthorize("hasAuthority('ADMIN')")
    ResponseEntity<Payment> fromWinner(@RequestBody @jakarta.validation.Valid InvoiceFromEvent e) {
        return ResponseEntity.ok(service.createInvoice(e.auctionId(), e.sellerId(), e.winnerId(), e.amount(), e.auctionTitle()));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('ADMIN')")
    Payment refund(@PathVariable long id) { return service.refund(id); }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    List<Payment> all() { return service.all(); }
}
