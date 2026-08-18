package com.gridveritas.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per gridveritas-core instance that has ever started (ADR-013).
 * instance_id is the container hostname (Docker assigns this automatically -
 * no extra config needed). "Online" is derived by the caller from how recent
 * lastHeartbeatAt is, not stored as a flag - see InstanceRegistryService.
 */
@Entity
@Table(name = "instance_heartbeat")
public class InstanceHeartbeat {

    @Id
    @Column(name = "instance_id", length = 64)
    private String instanceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    protected InstanceHeartbeat() {
    }

    public InstanceHeartbeat(String instanceId, Instant startedAt, Instant lastHeartbeatAt) {
        this.instanceId = instanceId;
        this.startedAt = startedAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
