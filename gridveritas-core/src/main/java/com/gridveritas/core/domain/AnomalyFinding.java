package com.gridveritas.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A statistical/metadata anomaly detected off the critical verification path.
 * NOT produced by the LLM — see ADR-004. Append-only. A dedup_key prevents the
 * same finding from being recorded repeatedly across detection runs.
 */
@Entity
@Table(
        name = "anomaly_findings",
        uniqueConstraints = @UniqueConstraint(name = "uq_anomaly_dedup", columnNames = "dedup_key")
)
public class AnomalyFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Source the finding relates to; null for global findings. */
    @Column(name = "source_id")
    private UUID sourceId;

    @Column(nullable = false, length = 40)
    private String type;        // SEQUENCE_GAP | SIGNATURE_INVALID_SPIKE | SOURCE_SILENCE

    @Column(nullable = false, length = 20)
    private String severity;    // INFO | WARNING | CRITICAL

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "dedup_key", nullable = false, length = 255)
    private String dedupKey;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Double metricValue) {
        this.metricValue = metricValue;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }
}
