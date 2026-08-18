-- M14 DER MV&S reference workflow. These tables are a thin view over existing
-- attestations/merkle_roots — they do not copy payload or proof data.
-- settlement_records / settlement_attestations are append-only.

CREATE TABLE aggregators (
    id          uuid PRIMARY KEY,
    name        varchar(128) NOT NULL,
    party_role  varchar(32) NOT NULL,
    created_at  timestamp with time zone NOT NULL
);

CREATE TABLE der_resources (
    id            uuid PRIMARY KEY,
    aggregator_id uuid NOT NULL REFERENCES aggregators (id),
    name          varchar(128) NOT NULL,
    resource_type varchar(32) NOT NULL,
    external_id   varchar(128),
    created_at    timestamp with time zone NOT NULL
);

CREATE TABLE resource_sources (
    resource_id uuid NOT NULL REFERENCES der_resources (id),
    source_id   uuid NOT NULL REFERENCES sources (id),
    PRIMARY KEY (resource_id, source_id)
);

CREATE TABLE settlement_records (
    id           uuid PRIMARY KEY,
    resource_id  uuid NOT NULL REFERENCES der_resources (id),
    period_start timestamp with time zone NOT NULL,
    period_end   timestamp with time zone NOT NULL,
    market       varchar(32) NOT NULL,
    format_name  varchar(128) NOT NULL,
    created_at   timestamp with time zone NOT NULL,
    CONSTRAINT chk_settlement_period CHECK (period_end > period_start)
);

CREATE TABLE settlement_attestations (
    settlement_id  uuid NOT NULL REFERENCES settlement_records (id),
    attestation_id uuid NOT NULL REFERENCES attestations (id),
    PRIMARY KEY (settlement_id, attestation_id)
);

CREATE INDEX idx_der_resources_aggregator ON der_resources (aggregator_id);
CREATE INDEX idx_settlement_records_resource ON settlement_records (resource_id, period_start);

GRANT SELECT, INSERT, UPDATE ON aggregators TO gridveritas_app;
GRANT SELECT, INSERT, UPDATE ON der_resources TO gridveritas_app;
GRANT SELECT, INSERT, DELETE ON resource_sources TO gridveritas_app;
GRANT SELECT, INSERT ON settlement_records TO gridveritas_app;
GRANT SELECT, INSERT ON settlement_attestations TO gridveritas_app;
