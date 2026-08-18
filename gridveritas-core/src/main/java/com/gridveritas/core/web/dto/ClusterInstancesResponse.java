package com.gridveritas.core.web.dto;

import java.time.Instant;
import java.util.List;

public record ClusterInstancesResponse(List<Instance> instances, long onlineCount) {

    public record Instance(String instanceId, Instant startedAt, Instant lastHeartbeatAt, boolean online) {
    }
}
