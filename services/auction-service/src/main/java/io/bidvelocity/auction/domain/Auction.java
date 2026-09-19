package io.bidvelocity.auction.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "auctions")
public class Auction {

    public enum Status { DRAFT, SCHEDULED, LIVE, ENDING, ENDED, CANCELLED, SOLD, UNSOLD }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false) private Long sellerId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String description = "";
    @Column(nullable = false) private String category;
    @Column(nullable = false) private String emoji = "📦";
    @Column(name = "image_url") private String imageUrl;

    @Column(name = "starting_price", nullable = false, precision = 14, scale = 2) private BigDecimal startingPrice;
    @Column(name = "current_price", nullable = false, precision = 14, scale = 2) private BigDecimal currentPrice;
    @Column(name = "min_increment", nullable = false, precision = 14, scale = 2) private BigDecimal minIncrement;
    @Column(name = "reserve_price", precision = 14, scale = 2) private BigDecimal reservePrice;

    @Column(name = "start_time", nullable = false) private Instant startTime;
    @Column(name = "end_time", nullable = false) private Instant endTime;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.DRAFT;

    @Column(name = "anti_sniping_enabled", nullable = false) private boolean antiSnipingEnabled = true;
    @Column(name = "extension_window_secs", nullable = false) private int extensionWindowSecs = 30;
    @Column(name = "max_extensions", nullable = false) private int maxExtensions = 3;
    @Column(name = "extension_count", nullable = false) private int extensionCount = 0;

    @Column(name = "bid_count", nullable = false) private int bidCount = 0;
    @Column(name = "highest_bid_id") private Long highestBidId;
    @Column(name = "winner_id") private Long winnerId;
    @Column(name = "winning_amount", precision = 14, scale = 2) private BigDecimal winningAmount;
    @Column(name = "close_reason") private String closeReason;

    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    @PreUpdate void touch() { updatedAt = Instant.now(); }

    public BigDecimal minNextBid() {
        return highestBidId == null ? startingPrice : currentPrice.add(minIncrement);
    }

    public boolean isOpen() { return status == Status.LIVE || status == Status.ENDING; }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; } public void setSellerId(Long v) { sellerId = v; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public String getCategory() { return category; } public void setCategory(String v) { category = v; }
    public String getEmoji() { return emoji; } public void setEmoji(String v) { emoji = v; }
    public String getImageUrl() { return imageUrl; } public void setImageUrl(String v) { imageUrl = v; }
    public BigDecimal getStartingPrice() { return startingPrice; } public void setStartingPrice(BigDecimal v) { startingPrice = v; }
    public BigDecimal getCurrentPrice() { return currentPrice; } public void setCurrentPrice(BigDecimal v) { currentPrice = v; }
    public BigDecimal getMinIncrement() { return minIncrement; } public void setMinIncrement(BigDecimal v) { minIncrement = v; }
    public BigDecimal getReservePrice() { return reservePrice; } public void setReservePrice(BigDecimal v) { reservePrice = v; }
    public Instant getStartTime() { return startTime; } public void setStartTime(Instant v) { startTime = v; }
    public Instant getEndTime() { return endTime; } public void setEndTime(Instant v) { endTime = v; }
    public Status getStatus() { return status; } public void setStatus(Status v) { status = v; }
    public boolean isAntiSnipingEnabled() { return antiSnipingEnabled; } public void setAntiSnipingEnabled(boolean v) { antiSnipingEnabled = v; }
    public int getExtensionWindowSecs() { return extensionWindowSecs; } public void setExtensionWindowSecs(int v) { extensionWindowSecs = v; }
    public int getMaxExtensions() { return maxExtensions; } public void setMaxExtensions(int v) { maxExtensions = v; }
    public int getExtensionCount() { return extensionCount; } public void setExtensionCount(int v) { extensionCount = v; }
    public int getBidCount() { return bidCount; } public void setBidCount(int v) { bidCount = v; }
    public Long getHighestBidId() { return highestBidId; } public void setHighestBidId(Long v) { highestBidId = v; }
    public Long getWinnerId() { return winnerId; } public void setWinnerId(Long v) { winnerId = v; }
    public BigDecimal getWinningAmount() { return winningAmount; } public void setWinningAmount(BigDecimal v) { winningAmount = v; }
    public String getCloseReason() { return closeReason; } public void setCloseReason(String v) { closeReason = v; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
