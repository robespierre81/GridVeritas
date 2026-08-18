# FERC Order No. 2222 MV&S reference mapping (M14)

Status: implemented as a **reference workflow**, not a market product.

This document is the requirements mapping promised by Architecture Baseline
ADR-014 / Project Document M14. It does **not** claim RTO/ISO certification,
endorsement, or the ability to execute settlement or move funds.

## Target format

**PJM-PowerMeter-interval-reference-v1** — field names follow PJM's publicly
described PowerMeter / hourly interval ideas (`datetime_beginning_utc`,
`datetime_ending_utc`, a resource identifier). Interval MW is **not**
fabricated: GridVeritas stores payload hashes and proofs, not meter registers.

## Evidentiary map

| FERC 2222 / market need | GridVeritas primitive |
|---|---|
| Who produced the DER reading | `sources.public_key` + Ed25519 over the canonical attestation |
| Measurement interval | attestation timestamp; `settlement_records.period_start` / `period_end` |
| Reading not altered after the fact | Merkle inclusion + `provenanceIntact` |
| Record existed by time T, including against the operator | RFC 3161 anchor on the covering root |
| Which aggregated resource the interval belongs to | `der_resources` + `resource_sources` |
| Which market party presented it | `aggregators.party_role` |

## API

- `GET /api/v1/settlements/mapping` — this catalog
- `GET /api/v1/resources/{id}/attestations`
- `GET /api/v1/settlements/{id}` — period view + per-interval proof flags
- `POST /api/v1/aggregators`, `/resources`, `/settlements` — admin setup

## Out of scope

Real-time dispatch, financial clearing, multi-RTO formats, and any statement
that PJM or another ISO has accepted this mapping.
