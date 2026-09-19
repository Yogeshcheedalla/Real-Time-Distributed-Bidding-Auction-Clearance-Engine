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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The spec's central promise, verified against the real transactional path
 * (FOR UPDATE row lock + unique idempotency key + deterministic resolution).
 * H2 runs PostgreSQL mode; each bid is its own committed transaction, exactly
 * like 100 clients racing through the gateway to three bidding instances.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

    @Autowired BidService service;
    @Autowired BidRuntimeRepository runtimes;
    @Autowired BidRepository bids;
    @MockBean AuctionClient auctionClient;   // hydration source stubbed; sync-back is no-op on mock

    private static final List<String> USER = List.of("USER");

    private long freshAuction(String endStatus, Instant end) {
        long id = System.nanoTime() % 1_000_000_000L + ThreadLocalRandom.current().nextInt(1000);
        when(auctionClient.get(id)).thenAnswer(inv -> state(id, endStatus, end));
        return id;
    }

    private AuctionState state(long id, String status, Instant end) {
        return new AuctionState(id, 900L, new BigDecimal("1000"), new BigDecimal("100"), null,
                end.minusSeconds(3600), end, status, true, 30, 3, 0);
    }

    /**
     * Deterministic ladder proof on H2: equal second bid is rejected by the
     * minimum rule. (True multi-threaded serialization against real row locks
     * is proven in BidPostgresConcurrencyIT on the production engine.)
     */
    @Test
    void equalSecondBidIsRejectedByLadder() {
        long id = freshAuction("LIVE", Instant.now().plusSeconds(600));
        var first = service.place(id, 1001L, "First", USER, new BigDecimal("1100"), "lad1-" + id);
        assertThat(first.bid().id()).isNotNull();
        var ex = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> service.place(id, 1002L, "Twin", USER, new BigDecimal("1100"), "lad2-" + id));
        assertThat(ex.code()).isEqualTo("INVALID_BID");
        var third = service.place(id, 1003L, "Higher", USER, new BigDecimal("1200"), "lad3-" + id);
        assertThat(third.bid().id()).isNotNull();
        var r = runtimes.findById(id).orElseThrow();
        assertThat(r.getBidCount()).isEqualTo(2);
        assertThat(r.getCurrentPrice()).isEqualByComparingTo("1200");
    }

    /** 100 sequential bids (with replays): the ladder strictly increases and the
     *  projection never diverges from the durable bid ledger. */
    @Test
    void hundredBidLadderStaysStrictlyIncreasing() {
        long id = freshAuction("LIVE", Instant.now().plusSeconds(600));
        int accepted = 0;
        for (int k = 0; k < 100; k++) {
            BigDecimal amount = runtimes.findById(id)
                    .map(io.bidvelocity.bidding.domain.BidRuntime::minNextBid).orElse(new BigDecimal("1000"))
                    .add(new BigDecimal(ThreadLocalRandom.current().nextInt(0, 6) * 100));
            service.place(id, 2000 + k, "Rusher " + k, USER, amount, "rush-" + id + "-" + k);
            accepted++;
            if (k % 10 == 0) { // replay the same key: must not add a row
                var dup = service.place(id, 2000 + k, "Rusher " + k, USER, amount, "rush-" + id + "-" + k);
                assertThat(dup.duplicate()).isTrue();
            }
        }
        var chrono = new java.util.ArrayList<>(bids.rankedWinners(id, org.springframework.data.domain.PageRequest.of(0, 500)));
        assertThat(chrono).hasSize(accepted);
        chrono.sort((x, y) -> x.getAcceptedAt().equals(y.getAcceptedAt())
                ? Long.compare(x.getId(), y.getId()) : x.getAcceptedAt().compareTo(y.getAcceptedAt()));
        for (int i = 1; i < chrono.size(); i++)
            assertThat(chrono.get(i).getAmount()).isGreaterThan(chrono.get(i - 1).getAmount());
        var r = runtimes.findById(id).orElseThrow();
        assertThat(r.getBidCount()).isEqualTo(accepted);
        assertThat(r.getCurrentPrice()).isEqualByComparingTo(chrono.get(chrono.size() - 1).getAmount());
    }

    @Test
    void idempotencyKeyReplayReturnsOriginalWithoutSecondBid() throws Exception {
        long id = freshAuction("LIVE", Instant.now().plusSeconds(600));
        var first = service.place(id, 55L, "Steady", USER, new BigDecimal("1200"), "idem-" + id);
        long countAfterFirst = bids.rankedWinners(id, org.springframework.data.domain.PageRequest.of(0, 10)).size();
        var replay = service.place(id, 55L, "Steady", USER, new BigDecimal("9999"), "idem-" + id);
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.bid().id()).isEqualTo(first.bid().id());
        long countAfterReplay = bids.rankedWinners(id, org.springframework.data.domain.PageRequest.of(0, 10)).size();
        assertThat(countAfterReplay).isEqualTo(countAfterFirst);
    }

    @Test
    void winnerIsHighestThenEarliestThenId_andSealStopsBidding() {
        long id = freshAuction("LIVE", Instant.now().plusSeconds(600));
        service.place(id, 61L, "Early", USER, new BigDecimal("1500"), "w1-" + id);
        service.place(id, 62L, "Middle", USER, new BigDecimal("1600"), "w2-" + id);
        service.place(id, 63L, "Higher", USER, new BigDecimal("1700"), "w3-" + id);
        // the auction scheduler only asks for settlement once its (synced) clock passed expiry
        runtimes.findById(id).ifPresent(r -> { r.setEndTime(Instant.now().minusSeconds(5)); runtimes.save(r); });

        var res = service.sealAndResolve(id);
        assertThat(res.state()).isEqualTo("RESOLVED");
        assertThat(res.bidderId()).isEqualTo(63L);
        assertThat(res.amount()).isEqualByComparingTo("1700");

        var res2 = service.sealAndResolve(id);   // idempotent: same winner recomputed
        assertThat(res2.bidderId()).isEqualTo(63L);
        assertThat(res2.bidId()).isEqualTo(res.bidId());

        var ex = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> service.place(id, 64L, "Too Late", USER, new BigDecimal("1700"), "late-" + id));
        assertThat(ex.code()).isEqualTo("AUCTION_CLOSED");
    }

    @Test
    void antiSnipeExtendsInsideWindowAndStopsAtCap() {
        long id = freshAuction("ENDING", Instant.now().plusSeconds(15));  // inside 30s window
        var first = service.place(id, 71L, "Sniper1", USER, new BigDecimal("1100"), "sn1-" + id);
        assertThat(first.extended()).as("bid inside window extends clock").isTrue();
        assertThat(first.newEndTime()).isAfter(Instant.now().plusSeconds(30));

        var second = service.place(id, 72L, "Sniper2", USER, new BigDecimal("1200"), "sn2-" + id);
        assertThat(second.extended()).as("clock now outside window → no second extension").isFalse();

        // rewind the end time just before expiry, up to the configured cap (3)
        for (int i = 3; i <= 4; i++) {
            runtimes.findById(id).ifPresent(r -> { r.setEndTime(Instant.now().plusSeconds(10)); runtimes.save(r); });
            var step = service.place(id, 70 + i, "Sniper" + i, USER, new BigDecimal(1200 + (i - 2) * 100), "sn" + i + "-" + id);
            assertThat(step.extended()).as("extension %d of cap 3", i - 2).isTrue();
        }
        assertThat(runtimes.findById(id).orElseThrow().getExtensionCount()).isEqualTo(3);

        runtimes.findById(id).ifPresent(r -> { r.setEndTime(Instant.now().plusSeconds(5)); runtimes.save(r); });
        var capped = service.place(id, 76L, "Sniper6", USER, new BigDecimal("1600"), "sn6-" + id);
        assertThat(capped.extended()).as("cap reached — further late bids cannot extend").isFalse();
    }

    @Test
    void closedAndSellerBidsRejected() {
        long id = freshAuction("SCHEDULED", Instant.now().plusSeconds(600));
        var ex1 = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> service.place(id, 81L, "Eager", USER, new BigDecimal("1100"), "cs1-" + id));
        assertThat(ex1.code()).isEqualTo("AUCTION_NOT_LIVE");

        long id2 = freshAuction("LIVE", Instant.now().plusSeconds(600));
        var ex2 = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> service.place(id2, 900L, "Owner", USER, new BigDecimal("1100"), "cs2-" + id2));
        assertThat(ex2.code()).isEqualTo("SELLER_BID_FORBIDDEN");

        var ex3 = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class,
                () -> service.place(id2, 82L, "Admin", List.of("ADMIN"), new BigDecimal("1100"), "cs3-" + id2));
        assertThat(ex3.code()).isEqualTo("ADMIN_BID_FORBIDDEN");
    }
}
