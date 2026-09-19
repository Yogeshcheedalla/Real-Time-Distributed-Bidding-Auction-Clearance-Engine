package io.bidvelocity.gateway.filters;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real filter-level tests for the edge JWT policy: valid token → identity
 * headers injected, expired/forged token → 401 JSON in the unified error
 * format, public routes pass, client-forged X-User-* headers are stripped.
 */
class JwtValidationFilterTest {

    static final String SECRET = "unit-test-secret-at-least-32-bytes-long!!";
    final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    final JwtValidationFilter filter = new JwtValidationFilter(SECRET);

    private String token(long ttlMillis) {
        return Jwts.builder().subject("usr_1").claim("email", "a@b.io").claim("roles", List.of("USER"))
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key).compact();
    }

    private MockServerWebExchange exchange(String path, String auth) {
        MockServerHttpRequest.BaseBuilder<?> req = MockServerHttpRequest.get(path);
        if (auth != null) req.header("Authorization", auth);
        return MockServerWebExchange.from(req.header("X-User-Id", "HACKED").build());
    }

    @Test
    void publicGetAuctionsPassesWithoutToken() {
        var chain = new RecordingChain();
        filter.filter(exchange("/api/auctions", null), chain).block();
        assertThat(chain.seen).as("anonymous marketplace browsing reaches the service").isNotNull();
        assertThat(chain.seen.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void writeAuctionsRequiresToken() {
        var chain = new RecordingChain();
        var ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auctions").build());
        filter.filter(ex, chain).block();
        assertThat(chain.seen).as("POST without token is blocked at the edge").isNull();
        assertThat(ex.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void validTokenInjectsIdentityAndStripsForgedHeaders() {
        var chain = new RecordingChain();
        filter.filter(exchange("/api/bids", "Bearer " + token(60_000)), chain).block();
        assertThat(chain.seen).isNotNull();
        assertThat(chain.seen.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("usr_1");
        assertThat(chain.seen.getRequest().getHeaders().getFirst("X-User-Roles")).contains("USER");
    }

    @Test
    void expiredTokenRejected401() {
        var chain = new RecordingChain();
        var ex = exchange("/api/bids", "Bearer " + token(-1000));
        filter.filter(ex, chain).block();
        assertThat(chain.seen).isNull();
        assertThat(ex.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("INVALID_TOKEN");
    }

    @Test
    void wrongSignatureRejected() {
        var ex = exchange("/api/bids", "Bearer " + token(60_000) + "tampered");
        var chain = new RecordingChain();
        filter.filter(ex, chain).block();
        assertThat(ex.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void publicLoginPathPassesWithoutToken() {
        var chain = new RecordingChain();
        filter.filter(exchange("/api/auth/login", null), chain).block();
        assertThat(chain.seen).isNotNull();
        assertThat(chain.seen.getRequest().getHeaders().getFirst("X-User-Id")).isNull(); // forged header stripped
    }

    static class RecordingChain implements org.springframework.cloud.gateway.filter.GatewayFilterChain {
        org.springframework.web.server.ServerWebExchange seen;
        @Override public reactor.core.publisher.Mono<Void> filter(org.springframework.web.server.ServerWebExchange e) { seen = e; return reactor.core.publisher.Mono.empty(); }
    }
}
