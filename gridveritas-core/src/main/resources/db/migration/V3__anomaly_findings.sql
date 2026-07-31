-- GridVeritas anomaly findings (V3)
-- Statistical/metadata anomalies detected off the critical path. Append-only.

CREATE TABLE anomaly_findings (
    id           uuid         NOT NULL,
    source_id    uuid,
    type         varchar(40)  NOT NULL,
    severity     varchar(20)  NOT NULL,
    description  text         NOT NULL,
    metric_value double precision,
    window_start timestamp with time zone,
    window_end   timestamp with time zone,
    detected_at  timestamp with time zone NOT NULL,
    dedup_key    varchar(255) NOT NULL,
    CONSTRAINT pk_anomaly_findings PRIMARY KEY (id),
    CONSTRAINT uq_anomaly_dedup UNIQUE (dedup_key)
);

CREATE INDEX idx_anomaly_detected_at ON anomaly_findings (detected_at);
CREATE INDEX idx_anomaly_source      ON anomaly_findings (source_id);
