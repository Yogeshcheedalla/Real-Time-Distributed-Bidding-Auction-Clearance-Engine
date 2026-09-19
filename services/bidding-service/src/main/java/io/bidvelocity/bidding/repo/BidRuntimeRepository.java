package io.bidvelocity.bidding.repo;

import io.bidvelocity.bidding.domain.BidRuntime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface BidRuntimeRepository extends JpaRepository<BidRuntime, Long> {

    /** The serialization point: SELECT ... FOR UPDATE. Concurrent bids queue on this row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BidRuntime r WHERE r.auctionId = :id")
    Optional<BidRuntime> lockForUpdate(@Param("id") Long id);

    /**
     * The atomic acceptance gate: status open, clock open, and the bid meets the
     * ladder (≥ starting for a fresh auction, else current + increment) are all
     * evaluated inside the UPDATE's WHERE — i.e. against the row's CURRENT
     * committed values at lock time, not a stale Java read. 1 row = accepted,
     * 0 rows = rejected; no engine needs to promise read-your-race semantics.
     * Extension fields are guarded by extensionCount parity (optimistic cap).
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE BidRuntime r SET r.currentPrice = :amount, r.highestBidId = :bidId,
               r.bidCount = r.bidCount + 1, r.endTime = :endTime, r.extensionCount = :extCount,
               r.updatedAt = :now
         WHERE r.auctionId = :id
           AND r.status IN ('LIVE','ENDING')
           AND r.endTime > :now
           AND r.extensionCount = :expectedExtCount
           AND :amount >= CASE WHEN r.highestBidId IS NULL THEN r.startingPrice
                                ELSE r.currentPrice + r.minIncrement END
        """)
    int casAcceptBid(@Param("id") Long auctionId, @Param("bidId") Long bidId, @Param("amount") java.math.BigDecimal amount,
                     @Param("endTime") java.time.Instant endTime, @Param("extCount") int extCount,
                     @Param("expectedExtCount") int expectedExtCount, @Param("now") java.time.Instant now);

    Optional<BidRuntime> findByAuctionId(Long auctionId);
}
