package com.gridveritas.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limiter keyed by client (IP) and bucket type (a stricter
 * one for auth, a general one for everything else). The bucket math and
 * storage live behind RateLimitStore (in-memory for a single instance, Redis
 * for multiple - ADR-013); this class just knows the configured limits per
 * bucket type and namespaces keys so GENERAL/AUTH never collide.
 */
@Component
public class RateLimiter {

    public enum Bucket { GENERAL, AUTH }

    private final int generalPerMinute;
    private final int authPerMinute;
    private final RateLimitStore store;

    public RateLimiter(@Value("${gridveritas.security.rate-limit.general-per-minute:240}") int generalPerMinute,
                       @Value("${gridveritas.security.rate-limit.auth-per-minute:15}") int authPerMinute,
                       RateLimitStore store) {
        this.generalPerMinute = generalPerMinute;
        this.authPerMinute = authPerMinute;
        this.store = store;
    }

    /** @return true if allowed, false if the limit is exceeded. */
    public boolean tryConsume(Bucket type, String key) {
        int perMinute = (type == Bucket.AUTH) ? authPerMinute : generalPerMinute;
        return store.tryConsume(type.name() + ":" + key, perMinute);
    }
}
