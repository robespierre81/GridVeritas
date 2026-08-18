-- GridVeritas baseline schema (V1)
-- Matches the JPA entities so spring.jpa.hibernate.ddl-auto=validate passes.
-- Instant fields are mapped as "timestamp with time zone" (Hibernate 6 TIMESTAMP_UTC).

-- ---------------------------------------------------------------------------
-- sources  (mutable: last_seen_at is updated at runtime)
-- ---------------------------------------------------------------------------
CREATE TABLE sources (
    id           uuid         NOT NULL,
    name         varchar(255) NOT NULL,
    public_key   text,
    status       varchar(50)  NOT NULL,
    created_at   timestamp with time zone NOT NULL,
    last_seen_at timestamp with time zone,
    CONSTRAINT pk_sources PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- attestations  (append-only; leaf_hash is the Merkle leaf)
-- ---------------------------------------------------------------------------
CREATE TABLE attestations (
    id              uuid          NOT NULL,
    source_id       uuid          NOT NULL,
    payload_hash    varchar(128)  NOT NULL,
    "timestamp"     timestamp with time zone NOT NULL,
    sequence_nr     bigint        NOT NULL,
    signature       text          NOT NULL,
    signature_valid boolean,
    leaf_hash       varchar(64),
    created_at      timestamp with time zone NOT NULL,
    CONSTRAINT pk_attestations PRIMARY KEY (id),
    CONSTRAINT fk_attestations_source FOREIGN KEY (source_id) REFERENCES sources (id),
    CONSTRAINT uq_attestation_source_seq UNIQUE (source_id, sequence_nr)
);

CREATE INDEX idx_attestations_payload_hash ON attestations (payload_hash);
CREATE INDEX idx_attestations_source_ts    ON attestations (source_id, "timestamp");
CREATE INDEX idx_attestations_created      ON attestations (created_at, id);

-- ---------------------------------------------------------------------------
-- merkle_roots  (append-only; chained via prev_root_hash)
-- ---------------------------------------------------------------------------
CREATE TABLE merkle_roots (
    id             uuid        NOT NULL,
    root_hash      varchar(64) NOT NULL,
    prev_root_hash varchar(64),
    leaf_count     integer     NOT NULL,
    computed_at    timestamp with time zone NOT NULL,
    CONSTRAINT pk_merkle_roots PRIMARY KEY (id)
);

CREATE INDEX idx_merkle_roots_computed_at ON merkle_roots (computed_at);

-- ---------------------------------------------------------------------------
-- merkle_leaves  (append-only membership: one root per attestation)
-- ---------------------------------------------------------------------------
CREATE TABLE merkle_leaves (
    id         uuid        NOT NULL,
    root_id    uuid        NOT NULL,
    leaf_index integer     NOT NULL,
    att_id     uuid        NOT NULL,
    leaf_hash  varchar(64) NOT NULL,
    CONSTRAINT pk_merkle_leaves PRIMARY KEY (id),
    CONSTRAINT fk_merkle_leaves_root FOREIGN KEY (root_id) REFERENCES merkle_roots (id),
    CONSTRAINT fk_merkle_leaves_att  FOREIGN KEY (att_id)  REFERENCES attestations (id),
    CONSTRAINT uq_merkle_leaf_att UNIQUE (att_id)
);

CREATE INDEX idx_merkle_leaves_root ON merkle_leaves (root_id, leaf_index);


