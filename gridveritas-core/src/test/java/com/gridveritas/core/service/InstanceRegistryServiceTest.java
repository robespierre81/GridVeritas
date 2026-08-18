package com.gridveritas.core.service;

import com.gridveritas.core.domain.InstanceHeartbeat;
import com.gridveritas.core.repository.InstanceHeartbeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InstanceRegistryService answers "how many instances are online" (ADR-013):
 * registration on startup, periodic heartbeat, and deriving online/offline
 * from heartbeat recency rather than a stored flag (so a crashed instance -
 * no clean shutdown - still ages out correctly).
 */
@DataJpaTest
@ActiveProfiles("test")
class InstanceRegistryServiceTest {

    @Autowired
    private InstanceHeartbeatRepository repository;

    private InstanceRegistryService serviceFor(String hostname) {
        return new InstanceRegistryService(repository, hostname);
    }

    @Test
    void registerStartupCreatesARowForThisInstance() {
        InstanceRegistryService service = serviceFor("container-abc123");

        service.registerStartup();

        assertThat(repository.findById("container-abc123")).isPresent();
        assertThat(service.getInstanceId()).isEqualTo("container-abc123");
    }

    @Test
    void blankHostnameFallsBackToARandomLocalId() {
        InstanceRegistryService a = serviceFor("");
        InstanceRegistryService b = serviceFor("");

        assertThat(a.getInstanceId()).startsWith("local-");
        assertThat(a.getInstanceId()).isNotEqualTo(b.getInstanceId());
    }

    @Test
    void listInstancesReportsRecentHeartbeatsAsOnline() {
        InstanceRegistryService service = serviceFor("container-fresh");
        service.registerStartup();

        List<InstanceRegistryService.InstanceStatus> instances = service.listInstances();

        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).online()).isTrue();
    }

    @Test
    void listInstancesReportsStaleHeartbeatsAsOffline() {
        // Simulates a crashed instance: a row exists, but its heartbeat is old -
        // must be reported offline without needing any explicit shutdown signal.
        repository.save(new InstanceHeartbeat("container-dead",
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS)));

        InstanceRegistryService service = serviceFor("container-observer");
        List<InstanceRegistryService.InstanceStatus> instances = service.listInstances();

        InstanceRegistryService.InstanceStatus dead = instances.stream()
                .filter(i -> i.instanceId().equals("container-dead"))
                .findFirst().orElseThrow();
        assertThat(dead.online()).isFalse();
    }

    @Test
    void heartbeatUpdatesLastHeartbeatAtForAnAlreadyRegisteredInstance() {
        InstanceRegistryService service = serviceFor("container-heartbeat");
        service.registerStartup();
        Instant firstBeat = repository.findById("container-heartbeat").orElseThrow().getLastHeartbeatAt();

        service.heartbeat();

        Instant secondBeat = repository.findById("container-heartbeat").orElseThrow().getLastHeartbeatAt();
        assertThat(secondBeat).isAfterOrEqualTo(firstBeat);
    }

    @Test
    void listInstancesReflectsMultipleRegisteredInstancesTogether() {
        serviceFor("container-1").registerStartup();
        serviceFor("container-2").registerStartup();

        List<InstanceRegistryService.InstanceStatus> instances = serviceFor("container-3").listInstances();

        assertThat(instances).extracting(InstanceRegistryService.InstanceStatus::instanceId)
                .contains("container-1", "container-2");
        assertThat(instances).allMatch(InstanceRegistryService.InstanceStatus::online);
    }
}
