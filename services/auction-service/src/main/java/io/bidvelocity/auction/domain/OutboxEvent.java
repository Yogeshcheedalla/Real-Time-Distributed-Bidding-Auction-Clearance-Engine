package io.bidvelocity.auction.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Transactional outbox: written in the SAME tx as the state change; consumers poll /api/auctions/outbox. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false) private Long aggregateId;
    @Column(nullable = false) private String topic;
    @Column(nullable = false) private String payload;   // JSON text; jsonb cast in migration for PG
    @Column(nullable = false) private boolean published = false;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected OutboxEvent() {}
    public OutboxEvent(Long aggregateId, String topic, String payloadJson) {
        this.aggregateId = aggregateId; this.topic = topic; this.payload = payloadJson;
    }

    public Long getId() { return id; }
    public Long getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean v) { published = v; }
    public Instant getCreatedAt() { return createdAt; }
}
