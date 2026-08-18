# ADR-015 — Single-host PostgreSQL streaming replica and failover

**Status:** Accepted (Implemented)
**Date:** August 2026

## Context

ADR-013 scaled the application tier. The database remained one Postgres
container. A container crash still took down ingest, sealing, heartbeats, and
Flyway. Multi-region / Patroni / Kubernetes HA is still out of scope on this
8 GiB Debian host. The 99.9 % NFR is still not claimed.

## Decision

- `postgres-primary` keeps the existing `gridveritas-pgdata` volume, enables
  `wal_level=replica`, and ensures a `replicator` role + slot on every start
  (existing volumes never re-run `initdb`).
- `postgres-replica` clones via `pg_basebackup` into its own volume and runs
  hot standby.
- Applications connect to `postgres-rw` (HAProxy TCP). Primary is preferred;
  replica is backup.
- `postgres-watch` promotes the replica (`pg_promote()`) if the primary stays
  down for ~15 s, then freezes the old primary in HAProxy (`state maint`) so
  a restart cannot split-brain.
- Hostname alias `postgres` still points at the **primary** for anything that
  has not switched to `postgres-rw`.

## Consequences

### Positive
- A killed primary container no longer permanently stops writes once the
  replica is promoted.
- No etcd, no Patroni, no extra consensus store.

### Residual / honest limits
- Host death still takes both nodes. This is not multi-host HA and not 99.9 %.
- Replication is asynchronous: a few WAL records can be lost on promote
  (RPO > 0). Synchronous commit was rejected because a dead replica would
  stall writes on this small host.
- Rebuilding a standby after failover is a manual operator step.
- `docker-compose.mtls.yml` is unchanged.

## Numbering
Recorded in the ODTs as ADR-016 (the ODT series already used 014/015 for DER
and public-architecture publication).
