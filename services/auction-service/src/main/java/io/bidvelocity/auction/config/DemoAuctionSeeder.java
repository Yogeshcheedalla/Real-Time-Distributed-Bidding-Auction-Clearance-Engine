package io.bidvelocity.auction.config;

import io.bidvelocity.auction.domain.Auction;
import io.bidvelocity.auction.repo.AuctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Development-only demo catalogue (spec §65/§66). Idempotent: only when the
 * auctions table is empty. NO fabricated bid history — currentPrice starts at
 * startingPrice so the auction projection can never disagree with the (empty)
 * durable bid ledger. Demo sellerId 2 = seller@bidvelocity.io from auth seeding.
 */
@Component
@ConditionalOnProperty(name = "bidvelocity.demo.seed", havingValue = "true")
public class DemoAuctionSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAuctionSeeder.class);
    private final AuctionRepository auctions;

    public DemoAuctionSeeder(AuctionRepository auctions) { this.auctions = auctions; }

    @Override
    @Transactional
    public void run(String... args) {
        if (auctions.count() > 0) return;
        Instant now = Instant.now();
        auctions.save(a(2L, "Vintage Leica M3 Rangefinder Camera", "1959 mint-condition Leica M3 with original Summicron 50mm lens, leather case and paperwork. A collector-grade mechanical icon.", "Collectibles", "📷", "12000", "500", "20000", now.minus(Duration.ofMinutes(3)), now.plus(Duration.ofMinutes(4)), Auction.Status.LIVE, true, 30, 3));
        auctions.save(a(2L, "Nikon Z9 Mirrorless Flagship Body", "Sealed-box Nikon Z9, Indian warranty. 45.7MP stacked sensor, 8K/30p, dual EN-EL18D batteries included.", "Electronics", "📸", "32000", "1000", "42000", now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(9)), Auction.Status.LIVE, true, 30, 3));
        auctions.save(a(3L, "Hand-Knotted Kashmiri Silk Rug 9×12", "One-of-a-kind natural-dye Kashmiri silk rug, ~420 knots/inch, certified provenance.", "Home", "🧶", "25000", "2500", null, now.minus(Duration.ofMinutes(2)), now.plus(Duration.ofMinutes(14)), Auction.Status.LIVE, true, 30, 3));
        auctions.save(a(3L, "Rolex Submariner Date 116610LN", "2019 Rolex Submariner 40mm, full set with box and papers, documented service history.", "Luxury", "⌚", "950000", "25000", "1200000", now.plus(Duration.ofMinutes(2)), now.plus(Duration.ofMinutes(30)), Auction.Status.SCHEDULED, true, 30, 3));
        auctions.save(a(2L, "Yamaha NMAX 155 ABS — 2023, 4.2k km", "Single-owner NMAX 155 ABS, full service records, new rear tyre, no accidents.", "Vehicles", "🛵", "95000", "2000", null, now.plus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(60)), Auction.Status.SCHEDULED, true, 30, 3));
        log.info("demo auction catalogue seeded (6 lots: 3 live, 2 upcoming, anti-snipe on)");
    }

    private Auction a(Long seller, String title, String desc, String cat, String emoji,
                      String start, String inc, String reserve, Instant from, Instant to,
                      Auction.Status status, boolean snipe, int window, int maxExt) {
        Auction a = new Auction();
        a.setSellerId(seller); a.setTitle(title); a.setDescription(desc);
        a.setCategory(cat); a.setEmoji(emoji);
        a.setStartingPrice(new BigDecimal(start));
        a.setCurrentPrice(new BigDecimal(start));   // never fabricate price movement
        a.setMinIncrement(new BigDecimal(inc));
        a.setReservePrice(reserve == null ? null : new BigDecimal(reserve));
        a.setStartTime(from); a.setEndTime(to); a.setStatus(status);
        a.setAntiSnipingEnabled(snipe); a.setExtensionWindowSecs(window); a.setMaxExtensions(maxExt);
        return a;
    }
}
