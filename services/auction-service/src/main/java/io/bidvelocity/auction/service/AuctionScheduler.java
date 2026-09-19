package io.bidvelocity.auction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Lifecycle driver: activation, ending-mode, deterministic closing. Every pass is idempotent. */
@Component
@ConditionalOnProperty(name = "bidvelocity.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AuctionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);
    private final AuctionService service;

    public AuctionScheduler(AuctionService service) { this.service = service; }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        try {
            int started = service.activateDue();
            int ending = service.markEndingSoon();
            int closed = service.closeDue();
            if (started + ending + closed > 0) log.info("scheduler: {} started, {} ending, {} closed", started, ending, closed);
        } catch (Exception e) {
            log.error("scheduler tick failed", e);
        }
    }
}
