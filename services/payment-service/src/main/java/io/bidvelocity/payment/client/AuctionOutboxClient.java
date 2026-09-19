package io.bidvelocity.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

/**
 * Reads the Auction Service's transactional outbox for WINNER_DECLARED events
 * (at-least-once). Idempotency key auction:&lt;id&gt; makes replays a no-op.
 * When Kafka is enabled a @KafkaListener replaces this poller; the handler is unchanged.
 */
@FeignClient(name = "auction-service", url = "${bidvelocity.clients.auction:}")
public interface AuctionOutboxClient {

    record OutboxDto(long id, String topic, String aggregateId, String payload, java.time.Instant createdAt) {}

    @GetMapping("/api/auctions/outbox/WINNER_DECLARED")
    List<OutboxDto> poll(@RequestParam("after") long afterId);

    @PostMapping("/api/auctions/outbox/ack")
    void ack(@RequestBody List<Long> ids);
}
