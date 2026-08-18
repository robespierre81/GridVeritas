# ADR-016 — DER aggregation MV&S reference workflow (FERC Order No. 2222)

**Status:** Accepted (Implemented)
**Date:** August 2026

## Context

The Architecture Baseline (ODT ADR-014) chose wholesale DER aggregation under
FERC Order No. 2222 as the flagship application. The core already had
attestation, Merkle provenance, and RFC 3161 anchors. What was missing was a
thin, honest mapping onto aggregator / resource / settlement-period language
without pretending to clear a market.

## Decision

- Add `aggregators`, `der_resources`, `resource_sources`, append-only
  `settlement_records` and `settlement_attestations`.
- A settlement is a period window over existing attestations. Proofs are
  loaded from the existing `/proof` path, not copied.
- Export/mapping target is `PJM-PowerMeter-interval-reference-v1`.
- Do not invent MW values. Do not claim ISO certification.

## Consequences

Operators can demonstrate a settlement-shaped record with source signature,
inclusion, and anchor flags. Financial settlement and multi-ISO formats remain
out of scope.

## Numbering

ODT ADR-014 is this decision. This repository already used ADR-014 for
federation.
