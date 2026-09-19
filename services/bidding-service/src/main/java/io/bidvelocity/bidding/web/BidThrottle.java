package io.bidvelocity.bidding.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-process sliding-window throttle (Redis-INCR when Redis is provisioned). 12 bids/10s per user+auction. */
@Component
public class BidThrottle {
    private static final int LIMIT = 12;
    private static final long WINDOW_MS = 10_000;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(long userId, long auctionId) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(userId + ":" + auctionId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            if (q.size() >= LIMIT) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BID_THROTTLED", "Too many bids — slow down (max 12 per 10s)");
            }
            q.addLast(now);
        }
    }
}
