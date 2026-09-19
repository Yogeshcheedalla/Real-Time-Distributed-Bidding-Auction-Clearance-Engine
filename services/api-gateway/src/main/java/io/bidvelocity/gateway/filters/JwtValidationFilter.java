package io.bidvelocity.gateway.filters;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Validates the HS256 JWT at the edge. Protected routes receive trusted
 * X-User-* headers injected from verified claims; forged inbound copies of
 * those headers are always stripped. Public allowlist: auth entry points,
 * actuator and the WebSocket handshake (rooms authenticate by query token).
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
            "/api/auth/google", "/oauth2/authorization/", "/login/oauth2/",
            "/actuator/health", "/eureka/", "/ws/"
    );

    private final SecretKey key;

    public JwtValidationFilter(@Value("${bidvelocity.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("bidvelocity.jwt.secret must be at least 32 bytes (HMAC-SHA256)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    static final String ATTR_CLAIMS = "bv.claims";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(stripped(exchange));
        }
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(exchange, chain, HttpStatus.UNAUTHORIZED, "MISSING_TOKEN", "Authorization bearer token required");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(header.substring(7)).getPayload();
            ServerWebExchange mutated = stripped(exchange).mutate()
                    .request(r -> r.header("X-User-Id", claims.getSubject())
                            .header("X-User-Email", String.valueOf(claims.get("email")))
                            .header("X-User-Roles", String.join(",", claims.get("roles", List.class))))
                    .build();
            mutated.getAttributes().put(ATTR_CLAIMS, claims);
            return chain.filter(mutated);
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange, chain, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token invalid or expired");
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /** always remove client-supplied identity headers to prevent forgery */
    private ServerWebExchange stripped(ServerWebExchange exchange) {
        return exchange.mutate().request(r -> r.headers(h -> {
            h.remove("X-User-Id"); h.remove("X-User-Email"); h.remove("X-User-Roles");
        })).build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, GatewayFilterChain chain, HttpStatus status, String code, String message) {
        ServerHttpRequest req = exchange.getRequest();
        Object cid = req.getHeaders().getFirst("X-Correlation-Id");
        String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + code + "\",\"message\":\"" + message
                + "\",\"path\":\"" + req.getURI().getPath() + "\",\"correlationId\":\"" + (cid == null ? "unknown" : cid) + "\"}";
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() { return -100; }
}
