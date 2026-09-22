package io.bidvelocity.gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fixed-window counter, keyed by the AUTHENTICATED USER (JWT `sub`), not by IP.
 *
 * Why not IP: behind a single Cloudflare tunnel every candidate shares one source
 * address, so an IP bucket collapses all bidders into one budget and the first
 * user exhausts it (429) while the rest are locked out. Keying by user lets the
 * platform hold many concurrent bidders. Unauthenticated traffic (public reads)
 * falls back to the IP bucket with a generous read budget.
 *
 * The `sub` is decoded from the (unverified) JWT payload ONLY to pick a bucket —
 * it is not a trust decision; JwtValidationFilter still enforces auth downstream.
 * In-memory today; the same interface maps to Redis INCR when Redis is provisioned.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private static final Pattern SUB = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"");

    /** route class -> requests per minute per identity. Sized for 10-20 concurrent users. */
    private int limitFor(String routeClass) {
        return switch (routeClass) {
            case "bid"   -> 600;   // hot path: place-bid writes per user/min
            case "login" -> 30;    // brute-force guard
            case "read"  -> 4000;  // polling reads (marketplace, bid history, live feed)
            default      -> 2000;  // other authenticated writes
        };
    }

    private String routeClass(String path, String method) {
        if ("POST".equals(method) && path.startsWith("/api/bids")) return "bid";
        if (path.startsWith("/api/auth/login")) return "login";
        if ("GET".equals(method)) return "read";
        return "write";
    }

    private String identity(ServerWebExchange exchange) {
        String h = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (h != null && h.startsWith("Bearer ")) {
            String[] parts = h.substring(7).split("\\.");
            if (parts.length >= 2) {
                try {
                    String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    Matcher m = SUB.matcher(json);
                    if (m.find()) return "u:" + m.group(1);
                } catch (Exception ignore) { /* malformed -> fall back to IP */ }
            }
        }
        var ra = exchange.getRequest().getRemoteAddress();
        return "ip:" + (ra == null || ra.getAddress() == null ? "unknown" : ra.getAddress().getHostAddress());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String rc = routeClass(path, method);
        String bucket = (Instant.now().getEpochSecond() / 60) + ":" + identity(exchange) + ":" + rc;
        int used = counters.computeIfAbsent(bucket, k -> new AtomicInteger()).incrementAndGet();
        if (used > limitFor(rc)) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set("Retry-After", "5");
            String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests — retry shortly\",\"path\":\"" + path + "\",\"correlationId\":\"" + exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER) + "\"}";
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() { return -150; } // shed load cheaply, before auth
}
