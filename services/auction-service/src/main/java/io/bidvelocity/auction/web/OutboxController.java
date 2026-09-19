package io.bidvelocity.auction.web;

import io.bidvelocity.auction.domain.OutboxEvent;
import io.bidvelocity.auction.dto.Dtos.OutboxDto;
import io.bidvelocity.auction.repo.OutboxRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Outbox relay. The Payment Service polls this for WINNER_DECLARED events
 * (at-least-once); marking published is idempotent on the consumer side via
 * payment.auction_id unique key. When Kafka is enabled a relay publisher
 * drains this table instead — same events, same guarantees.
 */
@RestController
@RequestMapping("/api/auctions/outbox")
public class OutboxController {

    private final OutboxRepository outbox;
    public OutboxController(OutboxRepository outbox) { this.outbox = outbox; }

    @GetMapping("/{topic}")
    @Transactional(readOnly = true)
    public List<OutboxDto> poll(@PathVariable String topic, @RequestParam(defaultValue = "0") long after) {
        return outbox.findByTopicAndIdGreaterThanOrderByIdAsc(topic, after).stream()
                .map(e -> new OutboxDto(e.getId(), e.getTopic(), String.valueOf(e.getAggregateId()), e.getPayload(), e.getCreatedAt()))
                .toList();
    }

    @PostMapping("/ack")
    @Transactional
    public void ack(@RequestBody List<Long> ids) {
        outbox.markPublished(ids);
    }
}
