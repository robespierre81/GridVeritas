package com.gridveritas.core.service;

import com.gridveritas.core.domain.Attestation;
import com.gridveritas.core.domain.AnomalyFinding;
import com.gridveritas.core.repository.AnomalyFindingRepository;
import com.gridveritas.core.repository.AttestationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight, statistical anomaly detection on attestation METADATA. Runs off the
 * critical verification path (scheduled) and never touches ingest latency or the
 * throughput targets. Deliberately NOT an LLM task (ADR-004). Findings are stored
 * in anomaly_findings with a dedup key so each is recorded at most once.
 */
@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final AttestationRepository attestationRepository;
    private final AnomalyFindingRepository anomalyRepository;

    @Value("${gridveritas.anomaly.lookback-hours:24}")
    private long lookbackHours;

    @Value("${gridveritas.anomaly.min-samples:5}")
    private int minSamples;

    @Value("${gridveritas.anomaly.invalid-ratio-threshold:0.2}")
    private double invalidRatioThreshold;

    @Value("${gridveritas.anomaly.silence-threshold-ms:300000}")
    private long silenceThresholdMs;

    public AnomalyDetectionService(AttestationRepository attestationRepository,
                                   AnomalyFindingRepository anomalyRepository) {
        this.attestationRepository = attestationRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @Scheduled(fixedDelayString = "${gridveritas.anomaly.interval-ms:60000}")
    @Transactional
    public void detect() {
        Instant now = Instant.now();
        Instant since = now.minus(lookbackHours, ChronoUnit.HOURS);

        List<Attestation> recent =
                attestationRepository.findByCreatedAtAfterOrderBySourceIdAscSequenceNrAsc(since);
        if (recent.isEmpty()) {
            return;
        }

        // group by source, preserving the (sequence-ascending) order
        Map<UUID, List<Attestation>> bySource = new LinkedHashMap<>();
        for (Attestation a : recent) {
            bySource.computeIfAbsent(a.getSource().getId(), k -> new ArrayList<>()).add(a);
        }

        int newFindings = 0;
        for (Map.Entry<UUID, List<Attestation>> e : bySource.entrySet()) {
            UUID sourceId = e.getKey();
            List<Attestation> list = e.getValue();
            newFindings += detectSequenceGaps(sourceId, list);
            newFindings += detectSignatureInvalidSpike(sourceId, list, now);
            newFindings += detectSilence(sourceId, list, now);
        }
        if (newFindings > 0) {
            log.info("Anomaly detection: {} new finding(s) recorded", newFindings);
        }
    }

    private int detectSequenceGaps(UUID sourceId, List<Attestation> list) {
        int count = 0;
        for (int i = 1; i < list.size(); i++) {
            Long prev = list.get(i - 1).getSequenceNr();
            Long cur = list.get(i).getSequenceNr();
            if (prev == null || cur == null) {
                continue;
            }
            if (cur > prev + 1) {
                long fromMissing = prev + 1;
                long toMissing = cur - 1;
                long missing = toMissing - fromMissing + 1;
                String dedup = sourceId + ":SEQGAP:" + fromMissing + "-" + toMissing;
                if (record(dedup, sourceId, "SEQUENCE_GAP", "WARNING",
                        "Missing sequence numbers " + fromMissing + "–" + toMissing
                                + " for source " + sourceId + " (" + missing + " attestation(s) absent).",
                        (double) missing, null, null)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int detectSignatureInvalidSpike(UUID sourceId, List<Attestation> list, Instant now) {
        int total = list.size();
        if (total < minSamples) {
            return 0;
        }
        long invalid = list.stream().filter(a -> Boolean.FALSE.equals(a.getSignatureValid())).count();
        double ratio = (double) invalid / total;
        if (ratio >= invalidRatioThreshold) {
            Instant bucket = now.truncatedTo(ChronoUnit.HOURS);
            String dedup = sourceId + ":SIGSPIKE:" + bucket;
            String severity = ratio >= 0.5 ? "CRITICAL" : "WARNING";
            if (record(dedup, sourceId, "SIGNATURE_INVALID_SPIKE", severity,
                    "Invalid-signature ratio " + String.format("%.0f%%", ratio * 100)
                            + " (" + invalid + "/" + total + ") for source " + sourceId
                            + " over the last " + lookbackHours + "h.",
                    ratio, now.minus(lookbackHours, ChronoUnit.HOURS), now)) {
                return 1;
            }
        }
        return 0;
    }

    private int detectSilence(UUID sourceId, List<Attestation> list, Instant now) {
        Instant lastSeen = list.stream()
                .map(Attestation::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(null);
        if (lastSeen == null) {
            return 0;
        }
        long silentMs = now.toEpochMilli() - lastSeen.toEpochMilli();
        if (silentMs > silenceThresholdMs) {
            Instant bucket = lastSeen.truncatedTo(ChronoUnit.HOURS);
            String dedup = sourceId + ":SILENCE:" + bucket;
            if (record(dedup, sourceId, "SOURCE_SILENCE", "WARNING",
                    "Source " + sourceId + " has been silent for "
                            + (silentMs / 1000) + "s (last seen " + lastSeen + ").",
                    (double) silentMs, lastSeen, now)) {
                return 1;
            }
        }
        return 0;
    }

    private boolean record(String dedupKey, UUID sourceId, String type, String severity,
                           String description, Double metric, Instant windowStart, Instant windowEnd) {
        if (anomalyRepository.existsByDedupKey(dedupKey)) {
            return false;
        }
        AnomalyFinding f = new AnomalyFinding();
        f.setDedupKey(dedupKey);
        f.setSourceId(sourceId);
        f.setType(type);
        f.setSeverity(severity);
        f.setDescription(description);
        f.setMetricValue(metric);
        f.setWindowStart(windowStart);
        f.setWindowEnd(windowEnd);
        f.setDetectedAt(Instant.now());
        anomalyRepository.save(f);
        return true;
    }
}
