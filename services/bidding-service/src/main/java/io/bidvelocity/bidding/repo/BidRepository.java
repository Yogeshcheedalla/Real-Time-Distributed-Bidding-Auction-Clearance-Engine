package io.bidvelocity.bidding.repo;

import io.bidvelocity.bidding.domain.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    Optional<Bid> findByIdempotencyKey(String key);

    /** Deterministic winner: amount DESC, accepted_at ASC, id ASC — the tie-break ladder. */
    @Query("""
      SELECT b FROM Bid b
      WHERE b.auctionId = :auctionId AND b.status = 'ACCEPTED'
      ORDER BY b.amount DESC, b.acceptedAt ASC, b.id ASC
      """)
    List<Bid> rankedWinners(@org.springframework.data.repository.query.Param("auctionId") Long auctionId, Pageable page);

    Page<Bid> findByAuctionIdAndStatusOrderByIdDesc(Long auctionId, String status, Pageable page);

    List<Bid> findByBidderIdOrderByAcceptedAtDesc(Long bidderId);
}
