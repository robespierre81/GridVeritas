package com.gridveritas.core.web;

import com.gridveritas.core.service.InstanceRegistryService;
import com.gridveritas.core.web.dto.ClusterInstancesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cluster")
public class ClusterController {

    private final InstanceRegistryService instanceRegistryService;

    public ClusterController(InstanceRegistryService instanceRegistryService) {
        this.instanceRegistryService = instanceRegistryService;
    }

    /** How many gridveritas-core instances are online right now (ADR-013), and since when. */
    @GetMapping("/instances")
    public ClusterInstancesResponse instances() {
        List<InstanceRegistryService.InstanceStatus> all = instanceRegistryService.listInstances();
        long onlineCount = all.stream().filter(InstanceRegistryService.InstanceStatus::online).count();
        List<ClusterInstancesResponse.Instance> mapped = all.stream()
                .map(s -> new ClusterInstancesResponse.Instance(
                        s.instanceId(), s.startedAt(), s.lastHeartbeatAt(), s.online()))
                .toList();
        return new ClusterInstancesResponse(mapped, onlineCount);
    }
}
