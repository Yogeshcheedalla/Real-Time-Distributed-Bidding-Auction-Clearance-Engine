package io.bidvelocity.bidding.service;

import io.bidvelocity.bidding.client.AuctionClient;
import io.bidvelocity.bidding.dto.Dtos.AuctionState;
import io.bidvelocity.bidding.repo.BidRepository;
import io.bidvelocity.bidding.repo.BidRuntimeRepository;
import io.bidvelocity.bidding.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * REAL PostgreSQL concurrency proof (no H2 approximations, no Docker).
 * Runs ONLY when BV_PG_URL / BV_PG_USER / BV_PG_PASSWORD are present in the
 * environment (the test driver exports them from the local .env), so CI and
 * machines without PostgreSQL skip cleanly instead of faking a pass.
 *
 * Creates an isolated schema, migrates it with the production Flyway SQL,
 * then races 24 equal bids and 100 laddered bids through the FOR UPDATE +
 * ladder-CAS gate exactly as three real bidding-service instances would.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "BV_PG_URL", matches = ".+")
class BidPostgresConcurrencyIT {

    static final String SCHEMA = "bv_it_" + Long.toHexString(System.nanoTime());

    @DynamicPropertySource
    static void pg(DynamicPropertyRegistry r) {
        try (Connection c = DriverManager.getConnection(
                System.getenv("BV_PG_URL"), System.getenv("BV_PG_USER"), System.getenv("BV_PG_PASSWORD"));
             Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        } catch (Exception e) { throw new IllegalStateException("cannot create test schema on PostgreSQL", e); }

        String url = System.getenv("BV_PG_URL") + (System.getenv("BV_PG_URL").contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.datasource.username", () -> System.getenv("BV_PG_USER"));
        r.add("spring.datasource.password", () -> System.getenv("BV_PG_PASSWORD"));
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
        r.add("spring.flyway.schemas", () -> SCHEMA);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired BidService service;
    @Autowired BidRuntimeRepository runtimes;
    @Autowired BidRepository bids;
    @MockBean AuctionClient auctionClient;

    private static final List<String> USER = List.of("USER");

    private long auction(String status, Instant start, Instant end) {
        long id = Math.abs(ThreadLocalRandom.current().nextLong(1_000_000_000L)) + 7_000_000_000L;
        when(auctionClient.get(anyLong())).thenAnswer(inv -> new AuctionState(
                inv.getArgument(0), 900L, new BigDecimal("1000"), new BigDecimal("100"), null,
                start, end, status, true, 30, 3, 0));
        return id;
    }

    @Test
    void twentyFourEqualBidsExactlyOneWinsOnRealPostgres() throws Exception {
        long id = auction("LIVE", Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));

        int n = 24;
        var pool = Executors.newFixedThreadPool(n);
        var gate = new CountDownLatch(1);
        var accepted = new AtomicInteger();
        var dup = new AtomicInteger();
        var rejected = new AtomicInteger();
        var unexpected = new ConcurrentLinkedQueue<String>();
        var futures = new java.util.ArrayList<Future<?>>();
        for (int i = 0; i < n; i++) {
            final int k = i;
            futures.add(pool.submit(() -> {
                try {
                    gate.await();
                    var out = service.place(id, 3000 + k, "PG Bidder " + k, USER, new BigDecimal("1100"), "pg-race-" + id + "-" + k);
                    if (out.duplicate()) { dup.incrementAndGet(); } else { accepted.incrementAndGet(); }
                } catch (ApiException e) { rejected.incrementAndGet(); }
                catch (Exception e) { unexpected.add(e.getClass().getSimpleName() + ":" + e.getMessage()); }
            }));
        }
        gate.countDown();
        for (var f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(unexpected).as("no engine-level surprises under real PG row locking").isEmpty();
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(n - 1);
        var r = runtimes.findById(id).orElseThrow();
        assertThat(r.getBidCount()).isEqualTo(1);
        assertThat(r.getCurrentPrice()).isEqualByComparingTo("1100");
    }

    @Test
    void hundredBiddersLadderHoldsOnRealPostgres() throws Exception {
        long id = auction("LIVE", Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));
        int n = 100;
        var pool = Executors.newFixedThreadPool(24);
        var gate = new CountDownLatch(1);
        var ok = new AtomicInteger();
        var futures = new java.util.ArrayList<Future<?>>();
        for (int i = 0; i < n; i++) {
            final int k = i;
            futures.add(pool.submit(() -> {
                try {
                    gate.await();
                    BigDecimal guess = new BigDecimal(1000 + ThreadLocalRandom.current().nextInt(0, 30) * 100);
                    service.place(id, 4000 + k, "PG Rusher " + k, USER, guess, "pg-rush-" + id + "-" + k);
                    ok.incrementAndGet();
                } catch (Exception ignored) { }
            }));
        }
        gate.countDown();
        for (var f : futures) f.get(90, TimeUnit.SECONDS);
        pool.shutdown();

        var chrono = new java.util.ArrayList<>(bids.rankedWinners(id, org.springframework.data.domain.PageRequest.of(0, 200)));
        chrono.sort((x, y) -> x.getAcceptedAt().equals(y.getAcceptedAt())
                ? Long.compare(x.getId(), y.getId()) : x.getAcceptedAt().compareTo(y.getAcceptedAt()));
        assertThat(ok.get()).isEqualTo(chrono.size());
        for (int i = 1; i < chrono.size(); i++)
            assertThat(chrono.get(i).getAmount()).isGreaterThan(chrono.get(i - 1).getAmount());
        var r = runtimes.findById(id).orElseThrow();
        assertThat(r.getBidCount()).isEqualTo(chrono.size());
        assertThat(r.getCurrentPrice()).isEqualByComparingTo(chrono.get(chrono.size() - 1).getAmount());
        var w1 = service.sealAndResolve(id);
        var w2 = service.sealAndResolve(id);
        assertThat(w1.bidId()).isEqualTo(w2.bidId());   // deterministic on the durable ledger
    }
}
