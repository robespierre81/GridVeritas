package com.gridveritas.core.security;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis-backed shared rate limiting (ADR-013): with N gridveritas-core
 * instances behind a load balancer, this is what makes the configured limit
 * one effective limit per client instead of N separate ones (one per
 * instance's own in-memory map, see InMemoryRateLimitStore).
 *
 * Token-bucket math is identical to InMemoryRateLimitStore's - continuous
 * refill, capacity == configured per-minute rate - but runs as a single Lua
 * script executed atomically by Redis (EVAL is single-threaded server-side),
 * so concurrent requests for the same key from different instances still
 * can't race each other into over-admitting.
 */
@Component
@Profile("!test")
public class RedisRateLimitStore implements RateLimitStore {

    // KEYS[1] = bucket key, ARGV[1] = capacityPerMinute, ARGV[2] = nowMillis
    // Mirrors InMemoryRateLimitStore.TokenBucket exactly: capacity == perMinute,
    // refillPerMs == capacity / 60000. EXPIRE bounds memory the way the
    // in-memory store's scheduled cleanup() does, without needing a scheduled
    // job of its own - an idle bucket (well past its own refill period) simply
    // disappears and starts fresh at full capacity next time it's touched.
    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            local refillPerMs = capacity / 60000.0

            local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
            local tokens = tonumber(data[1])
            local lastRefill = tonumber(data[2])
            if tokens == nil then
                tokens = capacity
                lastRefill = now
            end

            local elapsed = now - lastRefill
            if elapsed > 0 then
                tokens = math.min(capacity, tokens + elapsed * refillPerMs)
                lastRefill = now
            end

            local allowed = 0
            if tokens >= 1.0 then
                tokens = tokens - 1.0
                allowed = 1
            end

            redis.call('HMSET', key, 'tokens', tostring(tokens), 'lastRefill', tostring(lastRefill))
            redis.call('EXPIRE', key, 120)

            return allowed
            """;

    private static final RedisScript<Long> REDIS_SCRIPT = new DefaultRedisScript<>(SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryConsume(String bucketKey, int capacityPerMinute) {
        Long allowed = redisTemplate.execute(REDIS_SCRIPT, List.of("ratelimit:" + bucketKey),
                String.valueOf(capacityPerMinute), String.valueOf(System.currentTimeMillis()));
        return allowed != null && allowed == 1L;
    }
}
