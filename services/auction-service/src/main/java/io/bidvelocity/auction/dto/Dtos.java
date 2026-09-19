package io.bidvelocity.auction.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class Dtos {
    public record CreateAuctionRequest(
            @NotBlank @Size(min = 4, max = 140) String title,
            @Size(max = 4000) String description,
            @NotBlank String category,
            String emoji,
            @Size(max = 500) String imageUrl,
            @NotNull @DecimalMin(value = "0.01") BigDecimal startingPrice,
            @NotNull @DecimalMin(value = "0.01") BigDecimal minIncrement,
            @DecimalMin(value = "0.01") BigDecimal reservePrice,
            @NotNull Instant startTime,
            @NotNull Instant endTime,
            Boolean antiSnipingEnabled,
            @Min(5) @Max(120) Integer extensionWindowSeconds,
            @Min(1) @Max(10) Integer maxExtensions) {}

    public record AuctionDto(Long id, Long sellerId, String title, String description, String category, String emoji, String imageUrl,
                             BigDecimal startingPrice, BigDecimal currentPrice, BigDecimal minIncrement, BigDecimal reservePrice,
                             Instant startTime, Instant endTime, String status, boolean antiSnipingEnabled,
                             int extensionWindowSecs, int maxExtensions, int extensionCount,
                             int bidCount, Long highestBidId, Long winnerId, BigDecimal winningAmount, String closeReason,
                             long version, Instant createdAt, Instant updatedAt, BigDecimal minNextBid) {}

    public record BidStateSync(BigDecimal currentPrice, Long highestBidId, int bidCount,
                               Instant extendedEndTime, Integer extensionCount) {}

    public record OutboxDto(long id, String topic, String aggregateId, String payload, Instant createdAt) {}
}
