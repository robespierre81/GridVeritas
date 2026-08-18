-- M13 federation: peer registry + append-only stored peer roots.
-- Roots published by *this* operator stay in merkle_roots/anchors; this
-- schema only holds what we fetched from other operators.

CREATE TABLE federation_peers (
    id              uuid PRIMARY KEY,
    name            varchar(128) NOT NULL,
    base_url        varchar(512) NOT NULL,
    public_key      varchar(128) NOT NULL,
    enabled         boolean NOT NULL DEFAULT true,
    last_fetched_at timestamp with time zone,
    last_error      varchar(512),
    created_at      timestamp with time zone NOT NULL
);

CREATE TABLE peer_roots (
    id                  uuid PRIMARY KEY,
    peer_id             uuid NOT NULL REFERENCES federation_peers (id),
    operator_id         varchar(64) NOT NULL,
    root_hash           varchar(64) NOT NULL,
    prev_root_hash      varchar(64),
    leaf_count          integer NOT NULL,
    computed_at         timestamp with time zone NOT NULL,
    operator_signature  varchar(256) NOT NULL,
    anchor_authority    varchar(255),
    anchor_token        bytea,
    signature_valid     boolean NOT NULL,
    anchor_valid        boolean NOT NULL,
    fetched_at          timestamp with time zone NOT NULL,
    CONSTRAINT uq_peer_root UNIQUE (peer_id, root_hash)
);

CREATE INDEX idx_peer_roots_fetched ON peer_roots (fetched_at DESC);

GRANT SELECT, INSERT, UPDATE ON federation_peers TO gridveritas_app;
GRANT SELECT, INSERT ON peer_roots TO gridveritas_app;
