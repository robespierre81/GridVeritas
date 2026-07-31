package com.gridveritas.core.web;

import com.gridveritas.core.domain.AnomalyFinding;
import com.gridveritas.core.repository.AnomalyFindingRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Read access to anomaly findings produced by the off-critical-path detector.
 */
@RestController
@RequestMapping("/api/v1")
public class AnomalyController {

    private final AnomalyFindingRepository anomalyRepository;

    public AnomalyController(AnomalyFindingRepository anomalyRepository) {
        this.anomalyRepository = anomalyRepository;
    }

    /** Most recent findings (optionally filtered by source). */
    @GetMapping("/anomalies")
    public List<AnomalyFinding> list(@RequestParam(required = false) UUID sourceId) {
        return (sourceId == null)
                ? anomalyRepository.findTop100ByOrderByDetectedAtDesc()
                : anomalyRepository.findBySourceIdOrderByDetectedAtDesc(sourceId);
    }
}
