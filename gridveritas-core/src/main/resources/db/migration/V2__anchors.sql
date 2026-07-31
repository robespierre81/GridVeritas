-- GridVeritas external anchors (V2)
-- RFC 3161 timestamp tokens over Merkle roots. Append-only.

CREATE TABLE anchors (
    id            uuid         NOT NULL,
    root_id       uuid         NOT NULL,
    authority     varchar(255) NOT NULL,
    token         bytea        NOT NULL,
    gen_time      timestamp with time zone,
    serial_number varchar(128),
    anchored_at   timestamp with time zone NOT NULL,
    CONSTRAINT pk_anchors PRIMARY KEY (id),
    CONSTRAINT fk_anchors_root FOREIGN KEY (root_id) REFERENCES merkle_roots (id)
);

CREATE INDEX idx_anchors_root ON anchors (root_id);
