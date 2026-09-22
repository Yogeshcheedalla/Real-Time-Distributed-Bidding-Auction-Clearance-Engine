package io.bidvelocity.bidding.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Per-auction serialization row. Every bid transaction takes SELECT ... FOR UPDATE on this row. */
@Entity
@Table(name = "bid_runtime")
public class BidRuntime {
    @Id @Column(name = "auction_id") private Long auctionId;
    @Column(name = "seller_id", nullable = false) private Long sellerId;
    @Column(nullable = false) private String status = "SCHEDULED"; // SCHEDULED|LIVE|ENDING|SEALED
    @Column(name = "starting_price", nullable = false, precision = 14, scale = 2) private BigDecimal startingPrice;
    @Column(name = "current_price", nullable = false, precision = 14, scale = 2) private BigDecimal currentPrice;
    @Column(name = "min_increment", nullable = false, precision = 14, scale = 2) private BigDecimal minIncrement;
    @Column(name = "start_time", nullable = false) private Instant startTime;
    @Column(name = "end_time", nullable = false) private Instant endTime;
    @Column(name = "anti_snipe", nullable = false) private boolean antiSnipe = true;
    @Column(name = "extension_window", nullable = false) private int extensionWindow = 30;
    @Column(name = "max_extensions", nullable = false) private int maxExtensions = 3;
    @Column(name = "extension_count", nullable = false) private int extensionCount = 0;
    @Column(name = "highest_bid_id") private Long highestBidId;
    @Column(name = "bid_count", nullable = false) private int bidCount = 0;
    @Column(name = "synced_version", nullable = false) private long syncedVersion = 0;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    public BigDecimal minNextBid() {
        return highestBidId == null ? startingPrice : currentPrice.add(BigDecimal.ONE);
    }
    public boolean acceptsBids() {
        return ("LIVE".equals(status) || "ENDING".equals(status)) && endTime.isAfter(Instant.now());
    }

    public Long getAuctionId() { return auctionId; } public void setAuctionId(Long v) { auctionId = v; }
    public Long getSellerId() { return sellerId; } public void setSellerId(Long v) { sellerId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public BigDecimal getStartingPrice() { return startingPrice; } public void setStartingPrice(BigDecimal v) { startingPrice = v; }
    public BigDecimal getCurrentPrice() { return currentPrice; } public void setCurrentPrice(BigDecimal v) { currentPrice = v; }
    public BigDecimal getMinIncrement() { return minIncrement; } public void setMinIncrement(BigDecimal v) { minIncrement = v; }
    public Instant getStartTime() { return startTime; } public void setStartTime(Instant v) { startTime = v; }
    public Instant getEndTime() { return endTime; } public void setEndTime(Instant v) { endTime = v; }
    public boolean isAntiSnipe() { return antiSnipe; } public void setAntiSnipe(boolean v) { antiSnipe = v; }
    public int getExtensionWindow() { return extensionWindow; } public void setExtensionWindow(int v) { extensionWindow = v; }
    public int getMaxExtensions() { return maxExtensions; } public void setMaxExtensions(int v) { maxExtensions = v; }
    public int getExtensionCount() { return extensionCount; } public void setExtensionCount(int v) { extensionCount = v; }
    public Long getHighestBidId() { return highestBidId; } public void setHighestBidId(Long v) { highestBidId = v; }
    public int getBidCount() { return bidCount; } public void setBidCount(int v) { bidCount = v; }
    public long getSyncedVersion() { return syncedVersion; } public void setSyncedVersion(long v) { syncedVersion = v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
}
