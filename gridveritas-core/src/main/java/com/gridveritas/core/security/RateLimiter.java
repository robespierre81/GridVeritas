package com.gridveritas.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiter keyed by client (IP). Two bucket classes:
 * a stricter one for auth, a general one for everything else. Suitable for a
 * single-host deployment; a distributed limiter (e.g. Redis) is a later step.
 */
@Component
public class RateLimiter {

    public enum Bucket { GENERAL, AUTH }

    private final int generalPerMinute;
    private final int authPerMinute;
    private final int maxKeys;

    private final ConcurrentHashMap<String, TokenBucket> general = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> auth = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${gridveritas.security.rate-limit.general-per-minute:240}") int generalPerMinute,
                       @Value("${gridveritas.security.rate-limit.auth-per-minute:15}") int authPerMinute,
                       @Value("${gridveritas.security.rate-limit.max-keys:100000}") int maxKeys) {
        this.generalPerMinute = generalPerMinute;
        this.authPerMinute = authPerMinute;
        this.maxKeys = maxKeys;
    }

    /** @return true if allowed, false if the limit is exceeded. */
    public boolean tryConsume(Bucket type, String key) {
        ConcurrentHashMap<String, TokenBucket> map = (type == Bucket.AUTH) ? auth : general;
        int perMinute = (type == Bucket.AUTH) ? authPerMinute : generalPerMinute;
        if (map.size() > maxKeys) {
            // fail-open guard against the map itself becoming a memory sink
            map.clear();
        }
        return map.computeIfAbsent(key, k -> new TokenBucket(perMinute)).tryConsume();
    }

    /** Periodically drop idle (full) buckets to bound memory. */
    @Scheduled(fixedDelay = 300_000L)
    public void cleanup() {
        general.values().removeIf(TokenBucket::isFull);
        auth.values().removeIf(TokenBucket::isFull);
    }

    /** Simple token bucket: capacity == refill per minute; continuous refill. */
    static final class TokenBucket {
        private final double capacity;
        private final double refillPerMs;
        private double tokens;
        private long lastRefill;

        TokenBucket(int perMinute) {
            this.capacity = perMinute;
            this.refillPerMs = perMinute / 60_000.0;
            this.tokens = perMinute;
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized boolean isFull() {
            refill();
            return tokens >= capacity;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerMs);
                lastRefill = now;
            }
        }
    }
}
