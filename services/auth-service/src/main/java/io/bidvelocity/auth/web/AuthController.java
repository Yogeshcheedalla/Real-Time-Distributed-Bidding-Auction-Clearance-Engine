package io.bidvelocity.auth.web;

import io.bidvelocity.auth.dto.Dtos.*;
import io.bidvelocity.auth.security.JwtAuthFilter.AuthPrincipal;
import io.bidvelocity.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
class AuthController {

    private final AuthService auth;
    AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/auth/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(r));
    }

    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @PostMapping("/auth/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest r) { return auth.refresh(r); }

    @PostMapping("/auth/logout")
    ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshRequest r) {
        auth.logout(r);
        return ResponseEntity.ok(Map.of("status", "LOGGED_OUT"));
    }

    @GetMapping("/users/me")
    UserDto me(@AuthenticationPrincipal AuthPrincipal p) { return auth.me(p.id()); }

    @GetMapping("/admin/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    List<UserDto> adminUsers() { return auth.listAll(); }

    @PatchMapping("/admin/users/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    UserDto setUserStatus(@AuthenticationPrincipal AuthPrincipal admin, @PathVariable long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (!List.of("ACTIVE", "SUSPENDED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "status must be ACTIVE or SUSPENDED");
        }
        if (id == admin.id()) throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_SUSPENSION", "You cannot change your own status");
        return auth.setStatus(id, status);
    }
}
