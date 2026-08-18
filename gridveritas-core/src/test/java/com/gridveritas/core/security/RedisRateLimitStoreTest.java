package com.gridveritas.core.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisRateLimitStore is what makes the rate limit ONE effective limit shared
 * across multiple gridveritas-core instances (ADR-013), instead of N separate
 * ones. Verifies the Lua script's token-bucket math against a real Redis.
 *
 * Needs Docker: skipped (not failed - see the Assumptions check below) on a
 * Docker-less host. This machine has none locally; verified by running the
 * suite on the deployment server, same as IngestLoadRunner earlier this
 * session. Container lifecycle is managed manually rather than via
 * @Testcontainers/@Container, whose own before-all hook would throw before
 * this class gets a chance to skip instead of fail.
 */
class RedisRateLimitStoreTest {

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisRateLimitStore store;

    @BeforeAll
    static void startRedis() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available - skipping RedisRateLimitStoreTest");

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        store = new RedisRateLimitStore(template);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @Test
    void allowsUpToCapacityThenBlocks() {
        String key = "test:" + System.nanoTime();

        assertThat(store.tryConsume(key, 3)).isTrue();
        assertThat(store.tryConsume(key, 3)).isTrue();
        assertThat(store.tryConsume(key, 3)).isTrue();
        assertThat(store.tryConsume(key, 3)).isFalse();
    }

    @Test
    void distinctKeysHaveIndependentBudgets() {
        String keyA = "test:" + System.nanoTime() + ":a";
        String keyB = "test:" + System.nanoTime() + ":b";

        assertThat(store.tryConsume(keyA, 1)).isTrue();
        assertThat(store.tryConsume(keyA, 1)).isFalse();
        assertThat(store.tryConsume(keyB, 1)).isTrue();
    }

    @Test
    void tokensRefillOverTime() throws InterruptedException {
        // Same rate/timing approach as InMemoryRateLimitStore's equivalent test:
        // 6000/minute = 0.1 tokens/ms, so a short sleep deterministically refills.
        String key = "test:" + System.nanoTime();
        for (int i = 0; i < 6000; i++) {
            store.tryConsume(key, 6000);
        }
        assertThat(store.tryConsume(key, 6000)).as("bucket should be fully drained").isFalse();

        Thread.sleep(50);

        assertThat(store.tryConsume(key, 6000))
                .as("50ms at 0.1 tokens/ms should have refilled at least one token")
                .isTrue();
    }

    @Test
    void sharedAcrossTwoIndependentClientsProvesItIsNotProcessLocal() {
        // The whole point of this store: two separate StringRedisTemplate
        // instances (standing in for two separate gridveritas-core JVMs) hitting
        // the same Redis must see the SAME bucket state - unlike
        // InMemoryRateLimitStore, where each JVM has its own map.
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory secondFactory = new LettuceConnectionFactory(config);
        secondFactory.afterPropertiesSet();
        try {
            StringRedisTemplate secondTemplate = new StringRedisTemplate(secondFactory);
            secondTemplate.afterPropertiesSet();
            RedisRateLimitStore secondInstance = new RedisRateLimitStore(secondTemplate);

            String key = "test:" + System.nanoTime();
            assertThat(store.tryConsume(key, 1)).isTrue();
            // Consumed via "instance 1"; "instance 2" must see the bucket as already empty.
            assertThat(secondInstance.tryConsume(key, 1)).isFalse();
        } finally {
            secondFactory.destroy();
        }
    }

    @Test
    void bucketExpiresAfterLongIdlePeriod() {
        // Bounds memory the way InMemoryRateLimitStore's scheduled cleanup() does,
        // without a scheduled job of its own.
        String key = "test:" + System.nanoTime();
        store.tryConsume(key, 1);

        Long ttlSeconds = connectionFactory.getConnection().keyCommands()
                .ttl(("ratelimit:" + key).getBytes());
        assertThat(ttlSeconds).as("bucket key should carry a positive TTL after being touched")
                .isNotNull().isGreaterThan(0);
    }
}
