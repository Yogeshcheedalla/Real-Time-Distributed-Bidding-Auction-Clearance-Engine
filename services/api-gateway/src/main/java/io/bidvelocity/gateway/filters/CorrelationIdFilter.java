package io.bidvelocity.gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Assigns (or preserves) a correlation id on every request/response pair. */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String inbound = exchange.getRequest().getHeaders().getFirst(HEADER);
        String cid = (inbound != null && !inbound.isBlank() && inbound.length() <= 64)
                ? inbound : UUID.randomUUID().toString().substring(0, 12);
        ServerHttpRequest mutated = exchange.getRequest().mutate().header(HEADER, cid).build();
        exchange.getResponse().getHeaders().set(HEADER, cid);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
