package com.gridveritas.core.security;

/**
 * Storage backend for {@link RateLimiter}'s token buckets. Separated from
 * RateLimiter itself so the same bucket math (continuous refill, one token per
 * request) can run against either process-local memory (single instance) or a
 * shared store (multiple instances behind a load balancer, ADR-013).
 */
public interface RateLimitStore {

    /** @return true if a token was available and consumed, false if the bucket is empty. */
    boolean tryConsume(String bucketKey, int capacityPerMinute);
}
