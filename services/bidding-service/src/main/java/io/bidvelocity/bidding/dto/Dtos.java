package io.bidvelocity.bidding.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public class Dtos {
    public record PlaceBidRequest(@NotNull Long auctionId,
                                  @NotNull @DecimalMin("0.01") BigDecimal amount,
                                  @NotBlank String idempotencyKey) {}

    public record BidDto(Long id, Long auctionId, Long bidderId, String bidderName,
                         BigDecimal amount, String status, String rejectReason, Instant at) {}

    public record AuctionState(Long id, Long sellerId, BigDecimal startingPrice, BigDecimal minIncrement,
                               BigDecimal reservePrice, Instant startTime, Instant endTime, String status,
                               boolean antiSnipingEnabled, int extensionWindowSecs, int maxExtensions, long version) {}

    /** Returned when the auction service asks the bid ledger who won (the deterministic resolution). */
    public record Winner(Long bidId, Long bidderId, BigDecimal amount, Instant at) {}
}
