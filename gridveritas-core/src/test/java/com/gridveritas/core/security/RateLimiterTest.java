package com.gridveritas.core.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Token-bucket correctness, plus the specific mechanics behind REACH-1000
 * (rate limiter bypass via spoofed X-Forwarded-For): the actual fix lives in
 * RequestGuardFilter's client-IP extraction, not here, but the map-size
 * fail-open guard this test exercises is the second half of that bug - an
 * attacker rotating enough distinct keys doesn't just get a fresh bucket per
 * request, they periodically wipe every real client's state too.
 */
class RateLimiterTest {

    private static RateLimiter newLimiter(int generalPerMinute, int authPerMinute, int maxKeys) {
        return new RateLimiter(generalPerMinute, authPerMinute, new InMemoryRateLimitStore(maxKeys));
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter limiter = newLimiter(3, 3, 100_000);

        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "1.2.3.4")).isTrue();
        // Fourth request within the same window exceeds the 3/minute limit.
        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "1.2.3.4")).isFalse();
    }

    @Test
    void differentKeysHaveIndependentBudgets() {
        RateLimiter limiter = newLimiter(1, 1, 100_000);

        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "1.1.1.1")).isTrue();
        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "1.1.1.1")).isFalse();
        // A different key is unaffected by 1.1.1.1 already being exhausted.
        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "2.2.2.2")).isTrue();
    }

    @Test
    void authAndGeneralBucketsAreIndependentForTheSameKey() {
        RateLimiter limiter = newLimiter(1, 1, 100_000);

        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "1.1.1.1")).isTrue();
        // Same client IP, but AUTH has its own separate budget from GENERAL.
        assertThat(limiter.tryConsume(RateLimiter.Bucket.AUTH, "1.1.1.1")).isTrue();
        assertThat(limiter.tryConsume(RateLimiter.Bucket.AUTH, "1.1.1.1")).isFalse();
    }

    @Test
    void tokensRefillOverTime() throws InterruptedException {
        // 6000/minute = 100/second = 0.1/ms, so a real (short, deterministic)
        // sleep refills enough tokens without a flaky wall-clock-dependent test.
        RateLimiter limiter = newLimiter(6000, 6000, 100_000);
        String key = "3.3.3.3";

        for (int i = 0; i < 6000; i++) {
            limiter.tryConsume(RateLimiter.Bucket.GENERAL, key);
        }
        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, key))
                .as("bucket should be fully drained")
                .isFalse();

        Thread.sleep(50);

        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, key))
                .as("50ms at 0.1 tokens/ms should have refilled at least one token")
                .isTrue();
    }

    @Test
    void exceedingMaxKeysClearsStateForEveryClientNotJustTheNewOne() {
        // This is the fail-open safety valve read alongside REACH-1000: it
        // exists so the map itself can't become an unbounded memory sink, but
        // it means an attacker who can mint enough distinct keys (trivial via
        // a spoofable header) also wipes rate-limit state for real clients.
        RateLimiter limiter = newLimiter(1, 1, 3);

        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "victim")).isTrue();
        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "victim"))
                .as("victim's single-request budget is now exhausted")
                .isFalse();

        // Push the map past maxKeys=3 with distinct attacker-controlled keys.
        limiter.tryConsume(RateLimiter.Bucket.GENERAL, "attacker-1");
        limiter.tryConsume(RateLimiter.Bucket.GENERAL, "attacker-2");
        limiter.tryConsume(RateLimiter.Bucket.GENERAL, "attacker-3");
        limiter.tryConsume(RateLimiter.Bucket.GENERAL, "attacker-4");

        assertThat(limiter.tryConsume(RateLimiter.Bucket.GENERAL, "victim"))
                .as("the map-size guard cleared all state, including the victim's exhausted bucket")
                .isTrue();
    }
}
