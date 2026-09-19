package io.bidvelocity.auction.service;

import io.bidvelocity.auction.client.BiddingClient;
import io.bidvelocity.auction.domain.Auction;
import io.bidvelocity.auction.dto.Dtos.BidStateSync;
import io.bidvelocity.auction.dto.Dtos.CreateAuctionRequest;
import io.bidvelocity.auction.repo.OutboxRepository;
import io.bidvelocity.auction.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Auction lifecycle against the real service + JPA stack (H2 PG-mode).
 * Winner data comes from a stubbed BiddingClient — bids belong to the
 * bidding service; the auction service never sees its tables (DB-per-service).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuctionLifecycleTest {

    @Autowired AuctionService service;
    @Autowired OutboxRepository outbox;
    @MockBean BiddingClient bidding;

    private CreateAuctionRequest req(Instant start, Instant end) {
        return new CreateAuctionRequest("Leica M3 Rangefinder", "mint", "Collectibles", "📷", null,
                new BigDecimal("12000"), new BigDecimal("500"), new BigDecimal("20000"), start, end,
                true, 30, 3);
    }

    @Test
    void creationValidations() {
        assertThatThrownBy(() -> service.create(1L, req(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plusSeconds(600))))
                .isInstanceOf(ApiException.class).hasMessageContaining("Start time");
        assertThatThrownBy(() -> service.create(1L, req(Instant.now().plusSeconds(60), Instant.now().plusSeconds(90))))
                .isInstanceOf(ApiException.class).hasMessageContaining("60s");
        assertThatThrownBy(() -> service.create(1L, new CreateAuctionRequest("ok ok", "", "Art", "🖼", null,
                new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("50"),
                Instant.now().plusSeconds(60), Instant.now().plus(1, ChronoUnit.HOURS), true, 30, 3)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Reserve");
    }

    @Test
    void scheduledBecomesLiveAndEmitsEvent() {
        var dto = service.create(7L, req(Instant.now().plusSeconds(1), Instant.now().plus(1, ChronoUnit.HOURS)));
        awaitAssertActivate(dto.id());
        var a = service.get(dto.id());
        assertThat(a.status()).isEqualTo("LIVE");
        assertThat(outbox.findByTopicAndIdGreaterThanOrderByIdAsc("AUCTION_STARTED", 0L))
                .anyMatch(e -> e.getPayload().contains("\"auctionId\":" + dto.id()));
    }

    private void awaitAssertActivate(long id) {
        try { Thread.sleep(1200); } catch (InterruptedException ignored) { }
        assertThat(service.activateDue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void closeWithWinnerSoldAndOutbox() {
        var a = saveLive("Rolex Submariner", new BigDecimal("950000"), null,
                Instant.now().minusSeconds(60)); // already past end
        when(bidding.winner(a.getId())).thenReturn(new BiddingClient.Resolution(
                "RESOLVED", 11L, 42L, new BigDecimal("1200000"), Instant.now(), null));
        int closed = service.closeDue();
        assertThat(closed).isGreaterThanOrEqualTo(1);
        var fresh = service.get(a.getId());
        assertThat(fresh.status()).isEqualTo("SOLD");
        assertThat(fresh.winnerId()).isEqualTo(42L);
        assertThat(fresh.winningAmount()).isEqualByComparingTo("1200000");
        assertThat(outbox.findByTopicAndIdGreaterThanOrderByIdAsc("WINNER_DECLARED", 0L))
                .anyMatch(e -> e.getPayload().contains("\"winnerId\":42"));
    }

    @Test
    void closeReserveNotMetGoesUnsold() {
        var a = saveLive("Rare Rug", new BigDecimal("25000"), new BigDecimal("40000"),
                Instant.now().minusSeconds(60));
        when(bidding.winner(a.getId())).thenReturn(new BiddingClient.Resolution(
                "RESOLVED", 12L, 43L, new BigDecimal("30000"), Instant.now(), null));
        service.closeDue();
        assertThat(service.get(a.getId()).status()).isEqualTo("UNSOLD");
        assertThat(service.get(a.getId()).closeReason()).isEqualTo("RESERVE_NOT_MET");
    }

    @Test
    void closeNoBidsGoesUnsold() {
        var a = saveLive("Odd Lot", new BigDecimal("100"), null, Instant.now().minusSeconds(60));
        when(bidding.winner(a.getId())).thenReturn(new BiddingClient.Resolution("NO_BIDS", null, null, null, null, null));
        service.closeDue();
        assertThat(service.get(a.getId()).status()).isEqualTo("UNSOLD");
    }

    @Test
    void openDeferKeepsAuctionRetryableAndHealsClock() {
        var a = saveLive("Sniped Extend", new BigDecimal("100"), null, Instant.now().minusSeconds(30));
        Instant healed = Instant.now().plusSeconds(45);
        when(bidding.winner(a.getId())).thenReturn(new BiddingClient.Resolution("OPEN_DEFER", null, null, null, null, healed));
        service.closeDue();
        var after = service.get(a.getId());
        assertThat(after.endTime()).isAfter(Instant.now());          // clock healed
        assertThat(after.status()).isIn("LIVE", "ENDING");            // not wrongly settled
    }

    @Test
    void closedAuctionCannotBeCancelledAgain() {
        var a = saveLive("Done Deal", new BigDecimal("100"), null, Instant.now().minusSeconds(60));
        when(bidding.winner(a.getId())).thenReturn(new BiddingClient.Resolution(
                "RESOLVED", 13L, 44L, new BigDecimal("500"), Instant.now(), null));
        service.closeDue();
        assertThatThrownBy(() -> service.cancel(a.getId(), 1L, true))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void antiSnipeExtensionSyncUpdatesEndAndEmits() {
        var a = saveLive("Ending Soon", new BigDecimal("1000"), null, Instant.now().plus(5, ChronoUnit.MINUTES));
        Instant newEnd = a.getEndTime().plusSeconds(30);
        var dto = service.syncBidState(a.getId(), new BidStateSync(new BigDecimal("1500"), 99L, 3, newEnd, 1));
        assertThat(dto.endTime()).isEqualTo(newEnd);
        assertThat(dto.currentPrice()).isEqualByComparingTo("1500");
        assertThat(outbox.findByTopicAndIdGreaterThanOrderByIdAsc("AUCTION_EXTENDED", 0L)).isNotEmpty();
    }

    @Test
    void versionIncrementsOnEveryMutation() {
        var a = saveLive("Versioned", new BigDecimal("100"), null, Instant.now().plus(1, ChronoUnit.HOURS));
        long v0 = a.getVersion();
        service.syncBidState(a.getId(), new BidStateSync(new BigDecimal("200"), 1L, 1, null, null));
        assertThat(service.get(a.getId()).version()).isGreaterThan(v0);
    }

    /* helper: persist a LIVE auction directly (bypasses creation time validation) */
    private Auction saveLive(String title, BigDecimal start, BigDecimal reserve, Instant end) {
        Auction a = new Auction();
        a.setSellerId(7L);
        a.setTitle(title);
        a.setCategory("Art");
        a.setStartingPrice(start);
        a.setCurrentPrice(start);
        a.setMinIncrement(new BigDecimal("50"));
        a.setReservePrice(reserve);
        a.setStartTime(end.minusSeconds(3600));
        a.setEndTime(end);
        a.setStatus(Auction.Status.LIVE);
        return auctions.save(a);
    }

    @Autowired io.bidvelocity.auction.repo.AuctionRepository auctions;
}
