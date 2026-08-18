package com.gridveritas.core.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MerkleService.sealNewLeaves() relies on pg_try_advisory_xact_lock (ADR-013)
 * so only one gridveritas-core instance actually seals per cycle when running
 * multiple instances - H2 (used by MerkleServiceTest) has no such function, so
 * this is verified against a real Postgres instead. Proves the underlying
 * Postgres mechanism directly with two independent JDBC connections (standing
 * in for two instances' own DB connections) rather than orchestrating
 * concurrent Spring/JPA transactions against a single, thread-confined
 * EntityManager, which wouldn't actually exercise two-connection contention
 * and would be substantially more fragile for no extra confidence - the
 * application-level wiring itself is a single `if (!acquired) return;`,
 * confidently correct by inspection once the primitive it calls is proven.
 *
 * Needs Docker: skipped (not failed) on a Docker-less host - container
 * lifecycle is managed manually (not via @Testcontainers/@Container) so this
 * class gets to check availability before attempting to start anything.
 */
class MerkleSealLeaderElectionTest {

    // Same fixed key MerkleService.SEAL_LOCK_KEY uses - kept in sync manually
    // since the constant is private; a mismatch here would only make this test
    // less meaningful, not the production code less correct.
    private static final long SEAL_LOCK_KEY = 928374651L;

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker not available - skipping MerkleSealLeaderElectionTest");

        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void onlyOneConnectionHoldsTheLockAtOnceAndItReleasesOnCommit() throws Exception {
        try (Connection connA = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Connection connB = DriverManager.getConnection(
                     postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connA.setAutoCommit(false);
            connB.setAutoCommit(false);

            assertThat(tryAdvisoryLock(connA))
                    .as("first connection acquires the lock").isTrue();
            assertThat(tryAdvisoryLock(connB))
                    .as("second connection must NOT acquire it while the first transaction is open")
                    .isFalse();

            connA.commit(); // pg_try_advisory_XACT_lock auto-releases here

            assertThat(tryAdvisoryLock(connB))
                    .as("after the first transaction commits, the lock is free again")
                    .isTrue();

            connB.rollback();
        }
    }

    private static boolean tryAdvisoryLock(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT pg_try_advisory_xact_lock(" + SEAL_LOCK_KEY + ")")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }
}
