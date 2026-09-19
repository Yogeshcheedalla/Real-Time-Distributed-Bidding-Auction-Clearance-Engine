package io.bidvelocity.auction.config;

import io.bidvelocity.auction.domain.Auction;
import io.bidvelocity.auction.repo.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the demo seeder: idempotent, honest (currentPrice == startingPrice —
 * never a fabricated price movement), and produces a browsable catalogue.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "bidvelocity.demo.seed=true")
class DemoAuctionSeederTest {

    @Autowired AuctionRepository auctions;
    @Autowired DemoAuctionSeeder seeder;

    @Test
    void seedsHonestDemoCatalogue() {
        seeder.run();  // @SpringBootTest does not execute CommandLineRunners
        List<Auction> all = auctions.findAll();
        assertThat(all).isNotEmpty();
        assertThat(all).allSatisfy(a ->
                assertThat(a.getCurrentPrice()).isEqualByComparingTo(a.getStartingPrice()));
        assertThat(all).anyMatch(a -> a.getStatus() == Auction.Status.LIVE);
        assertThat(all).anyMatch(a -> a.getStatus() == Auction.Status.SCHEDULED);
        assertThat(all).anyMatch(a -> a.isAntiSnipingEnabled());

        int before = auctions.findAll().size();
        seeder.run();  // idempotent: must not duplicate
        assertThat(auctions.findAll()).hasSize(before);
    }
}
