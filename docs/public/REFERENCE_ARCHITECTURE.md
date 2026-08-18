# GridVeritas reference architecture (public)

**Version:** 1.4 (August 2026)
**Status:** Implementation-aligned snapshot for independent review.
**Cite:** https://doi.org/10.5281/zenodo.21820827
**Source:** https://github.com/robespierre81/GridVeritas

GridVeritas is a verification fabric for critical grid data. The flagship
application is multi-party measurement, verification, and settlement of
aggregated DERs under FERC Order No. 2222. The core engine is domain-agnostic.

This file is the public-facing summary. The authoritative ADRs and threat
model live next to it in this package.

## Trust model

1. The source signs a canonical attestation (Ed25519).
2. The core chains attestations into Merkle roots (`prev_root_hash`).
3. Each root is submitted to an independent RFC 3161 timestamping authority.
4. A later edit of a stored record fails `provenanceIntact` against the
   anchored leaf.

This is **not** a blockchain. The operator is not a trusted witness — the
TSA token is.

## Implemented components

| Layer | What |
|---|---|
| Edge | Go agent |
| Core | Spring Boot 3.3 / Java 21 |
| Store | PostgreSQL (Flyway, append-only app role) + Redis (rate limits) |
| API | JWT writes, read key for reads |
| UI | React console |
| HA (app) | N core replicas, nginx LB, advisory-lock sealing |
| HA (DB, single host) | Streaming replica + HAProxy + promote watcher |
| Observability | Prometheus + Grafana |
| Federation | Signed, anchored root publish/fetch |
| MV&S reference | Aggregator / resource / settlement period view (M14) |

## What this package does not claim

- NERC CIP or RTO/ISO certification
- 99.9 % availability
- Multi-host database HA
- Execution of market settlement or movement of funds

## How to cite

See `CITATION.cff`. After a Zenodo deposit, add the DOI to
`docs/public/RELEASE.txt`.
