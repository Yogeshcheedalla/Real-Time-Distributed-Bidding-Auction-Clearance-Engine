package io.bidvelocity.bidding.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bids")
public class Bid {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auction_id", nullable = false) private Long auctionId;
    @Column(name = "bidder_id", nullable = false) private Long bidderId;
    @Column(name = "bidder_name", nullable = false) private String bidderName;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private String status = "ACCEPTED";
    @Column(name = "reject_reason") private String rejectReason;
    @Column(name = "idempotency_key", unique = true) private String idempotencyKey;
    @Column(name = "accepted_at", nullable = false) private Instant acceptedAt = Instant.now();

    public Long getId() { return id; }
    public Long getAuctionId() { return auctionId; } public void setAuctionId(Long v) { auctionId = v; }
    public Long getBidderId() { return bidderId; } public void setBidderId(Long v) { bidderId = v; }
    public String getBidderName() { return bidderName; } public void setBidderName(String v) { bidderName = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { amount = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getRejectReason() { return rejectReason; } public void setRejectReason(String v) { rejectReason = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public Instant getAcceptedAt() { return acceptedAt; } public void setAcceptedAt(Instant v) { acceptedAt = v; }
}
