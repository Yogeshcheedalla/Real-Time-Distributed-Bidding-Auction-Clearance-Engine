package io.bidvelocity.auction.repo;

import io.bidvelocity.auction.domain.Auction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    List<Auction> findByStatusAndStartTimeLessThanEqual(Auction.Status status, Instant cutoff);

    List<Auction> findByStatusInAndEndTimeLessThanEqual(Collection<Auction.Status> statuses, Instant cutoff);

    @Query("""
      SELECT a FROM Auction a
      WHERE (:q IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.description) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:category IS NULL OR a.category = :category)
        AND (:status IS NULL OR a.status = :status)
        AND (:minPrice IS NULL OR a.currentPrice >= :minPrice)
        AND (:maxPrice IS NULL OR a.currentPrice <= :maxPrice)
      """)
    Page<Auction> search(@Param("q") String q, @Param("category") String category,
                         @Param("status") Auction.Status status,
                         @Param("minPrice") java.math.BigDecimal minPrice,
                         @Param("maxPrice") java.math.BigDecimal maxPrice,
                         Pageable pageable);
}
