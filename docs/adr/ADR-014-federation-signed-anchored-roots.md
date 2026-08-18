# ADR-014 — Federation of signed, anchored Merkle roots

**Status:** Accepted (Implemented)
**Date:** August 2026

## Context

Independent GridVeritas operators need to cross-verify provenance without
shared infrastructure or mutual trust. The Architecture Baseline recorded this
as proposed ADR-013 (ODT numbering). This repository records the implemented
decision as ADR-014 (ADR-013 here is already multi-instance HA).

A blockchain was rejected in ADR-006. Federation must work with the existing
self-verifying pieces: the operator's Ed25519 signature and the RFC 3161 token.

## Decision

- Each operator has a persistent Ed25519 identity (key + UUID) shared by all
  core replicas via a named volume (`/app/operator`).
- `GET /api/v1/federation/info` and `GET /api/v1/federation/roots` publish that
  identity and this operator's sealed roots, each signed over the canonical
  message `GridVeritas-Federation-Root-v1`. These two GETs are permitAll
  (rate-limited); the payload is self-verifying.
- A small `federation_peers` registry (ADMIN) stores name, base URL, and the
  peer's public key.
- A scheduled client (and `POST .../peers/{id}/fetch`) pulls a peer's publish
  document, rejects a key mismatch, and stores each root in append-only
  `peer_roots` with local `signatureValid` / `anchorValid` flags.
- `POST /api/v1/federation/verify` checks a bundle without storing it.
- Attestations and Merkle leaves are not replicated. Roots + optional anchors
  only.

## Consequences

### Positive
- A second operator can verify this operator's history without trusting this
  host or sharing a database.
- No ledger, no new TSA type, no change to the source-signature trust model.

### Residual
- Publish GETs are unauthenticated (intentional). Do not put extra secrets in
  the published document.
- Anchor trust-pinning is still optional (REACH-1010).
- No automatic peer discovery; URLs are configured by an admin.
- mTLS between operators is out of scope.

## Numbering
ODT ADR-013 (federation, previously proposed) is this decision. ODT ADR-014
remains the DER MV&S target-application decision (not implemented).
