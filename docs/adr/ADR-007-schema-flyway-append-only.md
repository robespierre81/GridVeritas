# ADR-007 — Schema via Flyway; runtime append-only via a non-owner role

**Status:** Accepted  
**Date:** July 2026

## Context

The data model for attestations and provenance must be explicit, reviewable, and protected against accidental or normal-path mutation. Hibernate auto-DDL cannot reliably express constraints or the append-only intent.

## Decision

- Manage the database schema with **versioned Flyway migrations**.
- Run Hibernate in **validate** mode (no auto-DDL).
- Enforce append-only behaviour on `attestations`, `merkle_roots`, and `merkle_leaves` by having the application connect as a **non-owner role** with `SELECT`/`INSERT` only. Migrations run as the owner role.

## Consequences

### Positive
- Schema is explicit, reviewable, and reproducible across environments
- Append-only intent is actually enforced (table owners always bypass grants)
- Clear separation between migration and runtime privileges

### Negative / Trade-offs
- Requires careful role management
- Append-only is defence-in-depth only; the primary guarantee against operator tampering remains the Merkle chain + external anchor (ADR-006)

## Alternatives rejected
- Hibernate auto-DDL (`ddl-auto: update`) – convenient but unreviewable and unable to enforce append-only
- Single database role for both migrations and runtime – cannot enforce append-only
