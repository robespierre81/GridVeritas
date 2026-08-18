package com.gridveritas.core.config;

import com.gridveritas.core.service.InstanceRegistryService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Domain gauges scraped by Prometheus. Online count uses the same 30s heartbeat
 * window as GET /api/v1/cluster/instances (ADR-013).
 */
@Component
public class ClusterMetrics {

    public ClusterMetrics(MeterRegistry meters, InstanceRegistryService registry) {
        Gauge.builder("gridveritas.instances.online", registry,
                        r -> r.listInstances().stream().filter(InstanceRegistryService.InstanceStatus::online).count())
                .description("Core instances with a heartbeat in the last 30 seconds")
                .register(meters);
        Gauge.builder("gridveritas.instances.known", registry,
                        r -> (double) r.listInstances().size())
                .description("Rows in the instance heartbeat table (includes stale)")
                .register(meters);
    }
}
