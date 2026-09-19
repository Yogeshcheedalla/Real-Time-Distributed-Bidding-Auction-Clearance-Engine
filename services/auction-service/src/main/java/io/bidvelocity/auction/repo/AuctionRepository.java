package io.bidvelocity.auction.repo;

import io.bidvelocity.auction.domain.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long>, JpaSpecificationExecutor<Auction> {

    List<Auction> findByStatusAndStartTimeLessThanEqual(Auction.Status status, Instant cutoff);

    List<Auction> findByStatusInAndEndTimeLessThanEqual(Collection<Auction.Status> statuses, Instant cutoff);
}
