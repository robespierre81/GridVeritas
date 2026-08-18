package com.gridveritas.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-instance token-bucket storage: process-local memory, no external
 * dependency. Used under the {@code test} profile (and previously the only
 * implementation - see git history) so the test suite needs no Redis. Not
 * suitable across multiple instances: each instance would enforce its own
 * separate limit (RedisRateLimitStore is the multi-instance implementation,
 * ADR-013).
 */
@Component
@Profile("test")
public class InMemoryRateLimitStore implements RateLimitStore {

    private final int maxKeys;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimitStore(@Value("${gridveritas.security.rate-limit.max-keys:100000}") int maxKeys) {
        this.maxKeys = maxKeys;
    }

    @Override
    public boolean tryConsume(String bucketKey, int capacityPerMinute) {
        if (buckets.size() > maxKeys) {
            // fail-open guard against the map itself becoming a memory sink
            buckets.clear();
        }
        return buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(capacityPerMinute)).tryConsume();
    }

    /** Periodically drop idle (full) buckets to bound memory. */
    @Scheduled(fixedDelay = 300_000L)
    public void cleanup() {
        buckets.values().removeIf(TokenBucket::isFull);
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
