package com.gridveritas.core.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RateLimiter.tryConsume() is on every request's hot path (RequestGuardFilter
 * runs before Spring Security) and is shared across all threads handling
 * concurrent requests, so its correctness under real contention - not just
 * single-threaded logic - is what actually matters in production. These are
 * load/concurrency tests, not unit tests: they exercise many threads hammering
 * the same and different keys at once.
 */
class RateLimiterConcurrencyTest {

    @Test
    void concurrentRequestsForTheSameKeyNeverExceedTheConfiguredLimitEvenUnderContention() throws Exception {
        int limit = 200;
        RateLimiter limiter = new RateLimiter(limit, limit, new InMemoryRateLimitStore(100_000));
        String key = "contended-key";
        int threadCount = 32;
        int attemptsPerThread = 50; // 1600 total attempts against a 200 budget

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                ready.countDown();
                await(go);
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (limiter.tryConsume(RateLimiter.Bucket.GENERAL, key)) {
                        admitted.incrementAndGet();
                    }
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS); // all threads parked at the gate
        go.countDown();                   // release them at (nearly) the same instant
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // A tiny amount of refill can happen during the run (real-time token
        // bucket), so allow a small margin rather than asserting an exact count -
        // the property under test is "no meaningful over-admission under race,"
        // not "zero refill ever happens."
        assertThat(admitted.get())
                .as("token bucket must not over-admit under concurrent contention")
                .isBetween(limit, limit + 5);
    }

    @Test
    void concurrentRequestsForDistinctKeysDoNotInterfereWithEachOthersBudget() throws Exception {
        RateLimiter limiter = new RateLimiter(1, 1, new InMemoryRateLimitStore(100_000));
        int keyCount = 500;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger admitted = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(keyCount);

        for (int i = 0; i < keyCount; i++) {
            String key = "client-" + i;
            pool.submit(() -> {
                try {
                    // Each key gets exactly one allowed request - two threads racing
                    // on the SAME key here would fight over that one token, but every
                    // key here is distinct, so all keyCount requests must be admitted.
                    if (limiter.tryConsume(RateLimiter.Bucket.GENERAL, key)) {
                        admitted.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(admitted.get()).isEqualTo(keyCount);
    }

    @Test
    void sustainedHighThroughputAcrossManyKeysCompletesWithinABudget() throws Exception {
        // Not a precise benchmark (JIT warmup, shared CI hardware) - a smoke test
        // that the hot path has no gross lock-contention or O(n) regression that
        // would make it fall over under realistic load. maxKeys is sized above the
        // key count so the fail-open guard (REACH-1000's other half) doesn't fire
        // mid-run and skew the timing.
        RateLimiter limiter = new RateLimiter(1_000_000, 1_000_000, new InMemoryRateLimitStore(200_000));
        int threadCount = 8;
        int opsPerThread = 50_000;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicLong totalOps = new AtomicLong();

        long start = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            pool.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    String key = "load-" + threadIndex + "-" + (i % 1000); // 1000 keys per thread
                    limiter.tryConsume(RateLimiter.Bucket.GENERAL, key);
                    totalOps.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(totalOps.get()).isEqualTo((long) threadCount * opsPerThread);
        assertThat(elapsedMs)
                .as("%d tryConsume() calls across %d threads took %dms - expected well under 10s on any reasonable hardware",
                        totalOps.get(), threadCount, elapsedMs)
                .isLessThan(10_000);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
