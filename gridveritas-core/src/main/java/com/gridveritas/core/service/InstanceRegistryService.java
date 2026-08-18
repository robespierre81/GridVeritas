package com.gridveritas.core.service;

import com.gridveritas.core.domain.InstanceHeartbeat;
import com.gridveritas.core.repository.InstanceHeartbeatRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Answers "how many gridveritas-core instances are online right now" (ADR-013)
 * via a shared Postgres table rather than relying on Docker/Jenkins to know:
 * each instance registers itself on startup under its own instance id (the
 * container hostname - Docker sets this automatically) and heartbeats
 * periodically. "Online" is derived from heartbeat recency, not stored as a
 * flag, so a crashed instance (no clean shutdown) still ages out correctly.
 */
@Service
public class InstanceRegistryService {

    private static final Logger log = LoggerFactory.getLogger(InstanceRegistryService.class);

    /** 3x the heartbeat interval: tolerates one or two missed beats before going offline. */
    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(30);

    private final InstanceHeartbeatRepository repository;
    private final String instanceId;

    public InstanceRegistryService(InstanceHeartbeatRepository repository,
                                   @Value("${HOSTNAME:}") String hostnameEnv) {
        this.repository = repository;
        this.instanceId = (hostnameEnv != null && !hostnameEnv.isBlank())
                ? hostnameEnv
                : "local-" + UUID.randomUUID();
    }

    public String getInstanceId() {
        return instanceId;
    }

    @PostConstruct
    @Transactional
    public void registerStartup() {
        Instant now = Instant.now();
        InstanceHeartbeat heartbeat = repository.findById(instanceId)
                .orElseGet(() -> new InstanceHeartbeat(instanceId, now, now));
        // A restarted instance can reuse the same hostname (Docker recreates the
        // container with a new ID on redeploy, but restart-in-place keeps it) -
        // treat this as a fresh start either way.
        heartbeat.setStartedAt(now);
        heartbeat.setLastHeartbeatAt(now);
        repository.save(heartbeat);
        log.info("Instance registered: {}", instanceId);
    }

    @Scheduled(fixedDelay = 10_000L)
    @Transactional
    public void heartbeat() {
        repository.findById(instanceId).ifPresent(h -> {
            h.setLastHeartbeatAt(Instant.now());
            repository.save(h);
        });
    }

    @Transactional(readOnly = true)
    public List<InstanceStatus> listInstances() {
        Instant cutoff = Instant.now().minus(ONLINE_WINDOW);
        return repository.findAll().stream()
                .map(h -> new InstanceStatus(h.getInstanceId(), h.getStartedAt(), h.getLastHeartbeatAt(),
                        h.getLastHeartbeatAt().isAfter(cutoff)))
                .toList();
    }

    public record InstanceStatus(String instanceId, Instant startedAt, Instant lastHeartbeatAt, boolean online) {
    }
}
