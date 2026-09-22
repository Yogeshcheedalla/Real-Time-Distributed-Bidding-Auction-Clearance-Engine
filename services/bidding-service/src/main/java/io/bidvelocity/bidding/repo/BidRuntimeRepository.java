package io.bidvelocity.bidding.repo;

import io.bidvelocity.bidding.domain.BidRuntime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface BidRuntimeRepository extends JpaRepository<BidRuntime, Long> {

    /** The serialization point: explicit native SELECT ... FOR UPDATE. Concurrent bids queue on this row. */
    @Query(value = "SELECT * FROM bid_runtime WHERE auction_id = :id FOR UPDATE", nativeQuery = true)
    Optional<BidRuntime> lockForUpdate(@Param("id") Long id);

    /** Advisory transaction lock: serializes all bid transactions for one auction across every
     *  bidding-service instance until commit — PostgreSQL's canonical hot-row pattern. */
    @Query(value = "SELECT pg_advisory_xact_lock(:id)", nativeQuery = true)
    Object advisoryLock(@Param("id") Long id);

    /**
     * True compare-and-swap acceptance gate. The update only lands if the row
     * still holds EXACTLY the price and top-bid the validator read under the
     * FOR UPDATE lock (null-safe), the auction is open, and the bid meets the
     * ladder. 1 row = accepted; 0 rows = someone moved the price first.
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
           AND r.currentPrice = :expectedPrice
           AND ((r.highestBidId IS NULL AND :expectedHighestId IS NULL) OR r.highestBidId = :expectedHighestId)
           AND :amount >= CASE WHEN r.highestBidId IS NULL THEN r.startingPrice
                                ELSE r.currentPrice + 1 END
        """)
    int casAcceptBid(@Param("id") Long auctionId, @Param("bidId") Long bidId, @Param("amount") java.math.BigDecimal amount,
                     @Param("endTime") java.time.Instant endTime, @Param("extCount") int extCount,
                     @Param("expectedExtCount") int expectedExtCount,
                     @Param("expectedPrice") java.math.BigDecimal expectedPrice,
                     @Param("expectedHighestId") Long expectedHighestId,
                     @Param("now") java.time.Instant now);

    Optional<BidRuntime> findByAuctionId(Long auctionId);
}
