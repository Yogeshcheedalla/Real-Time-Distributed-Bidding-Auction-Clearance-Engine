package io.bidvelocity.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments")
public class Payment {
    public enum Status { PENDING, PROCESSING, SUCCESS, FAILED, EXPIRED, REFUNDED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "auction_id", nullable = false) private Long auctionId;
    @Column(name = "auction_title", nullable = false) private String auctionTitle = "";
    @Column(name = "seller_id", nullable = false) private Long sellerId;
    @Column(name = "winner_id", nullable = false) private Long winnerId;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private String currency = "INR";
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.PENDING;
    @Column(nullable = false) private String provider = "MOCK";
    @Column(name = "provider_ref") private String providerRef;
    @Column(name = "failure_reason") private String failureReason;
    @Column(nullable = false) private int attempts = 0;
    @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getAuctionId() { return auctionId; } public void setAuctionId(Long v) { auctionId = v; }
    public String getAuctionTitle() { return auctionTitle; } public void setAuctionTitle(String v) { auctionTitle = v; }
    public Long getSellerId() { return sellerId; } public void setSellerId(Long v) { sellerId = v; }
    public Long getWinnerId() { return winnerId; } public void setWinnerId(Long v) { winnerId = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { amount = v; }
    public String getCurrency() { return currency; } public void setCurrency(String v) { currency = v; }
    public Status getStatus() { return status; } public void setStatus(Status v) { status = v; }
    public String getProvider() { return provider; } public void setProvider(String v) { provider = v; }
    public String getProviderRef() { return providerRef; } public void setProviderRef(String v) { providerRef = v; }
    public String getFailureReason() { return failureReason; } public void setFailureReason(String v) { failureReason = v; }
    public int getAttempts() { return attempts; } public void setAttempts(int v) { attempts = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
}
