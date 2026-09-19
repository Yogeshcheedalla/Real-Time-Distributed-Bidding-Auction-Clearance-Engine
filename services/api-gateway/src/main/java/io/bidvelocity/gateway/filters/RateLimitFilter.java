package io.bidvelocity.gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window counter per client-ip + route, in-memory today; the same
 * interface will be backed by Redis INCR when Redis is provisioned
 * (documented in docs/infrastructure.md). Bid routes get a tighter budget.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    private int limitFor(String path) {
        if (path.startsWith("/api/bids")) return 60;      // hot path: 60 req/min per IP
        if (path.startsWith("/api/auth/login")) return 10; // brute-force guard
        return 300;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() == null ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String path = exchange.getRequest().getURI().getPath();
        String bucket = LocalDate.now() + ":" + (Instant.now().getEpochSecond() / 60) + ":" + ip + ":" + path.substring(0, Math.min(path.length(), 9));
        int used = counters.computeIfAbsent(bucket, k -> new AtomicInteger()).incrementAndGet();
        if (used > limitFor(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests — retry shortly\",\"path\":\"" + path + "\",\"correlationId\":\"" + exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER) + "\"}";
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() { return -150; } // before auth: shed load cheaply
}
