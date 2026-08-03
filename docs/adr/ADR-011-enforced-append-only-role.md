# ADR-011 — Enforced append-only via a runtime non-owner role, provisioned by migration

**Status:** Accepted (Implemented)  
**Date:** August 2026

## Context

ADR-007 established the principle of a non-owner application role. The concrete provisioning mechanism must be reliable across redeploys and independent of Docker volume initialisation timing.

## Decision

- The application connects as the non-owner role `gridveritas_app` with `SELECT`/`INSERT` on append-only tables (and `UPDATE` on `sources` only).
- Migrations run as the table owner.
- The role and its grants are created by a **versioned Flyway migration** (`V5__app_role.sql`) rather than a Postgres init-hook script.

## Consequences

### Positive
- Deterministic and independent of `docker-entrypoint-initdb.d` timing
- Survives volume recreation and redeploys cleanly
- Hardens the runtime path and a compromised application process

### Negative / Trade-offs
- Does not constrain the owner role (required for DDL)
- Primary guarantee against deliberate operator tampering remains the external anchor (ADR-006)

## Note
This ADR hardens the implementation detail of the append-only model introduced in ADR-007.
