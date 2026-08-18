# ADR-006 — Provenance & anchoring: Merkle + RFC 3161, no blockchain

**Status:** Accepted  
**Date:** July 2026

## Context

The core trust requirement is to prove that operational data was not altered between source and decision point, and that the platform operator cannot silently rewrite history. A solution is needed that is efficient, does not introduce consensus overhead, and works for a solo MVP.

## Decision

- Establish provenance with **in-core hash-linked / Merkle chaining**.
- Make the record tamper-evident against the operator by periodically anchoring Merkle roots to one or more independent **RFC 3161 timestamping authorities**.
- **Do not use a blockchain or distributed ledger**.

## Consequences

### Positive
- Source signatures + external anchoring meet the trust requirement
- Anchoring cost is constant per period, independent of attestation throughput
- No consensus protocol or ledger operational complexity
- Independent parties can verify without trusting the platform operator

### Negative / Trade-offs
- Relies on the availability and long-term trustworthiness of the chosen TSA(s)
- A single TSA is a soft single point of trust (mitigated by supporting multiple TSAs and retaining tokens locally)

## Alternatives rejected
- **Permissioned or public blockchain**: disproportionate cost and complexity for a solo MVP; genuine decentralization would require a multi-party consortium that does not yet exist.
- **Database permissions alone**: insufficient, because a privileged operator can bypass them. The external anchor closes that gap.

## Later-phase option
Stronger anchoring via multi-witness co-signing or a transparency-log deployment, if a consortium of independent parties emerges.

## Validation (August 2026)
`MerkleService` is covered by `MerkleServiceTest` (see ADR-012): sealing and
root-chaining, tamper detection after sealing, and — the guarantee an
independent verifier actually relies on — reconstructing the root hash from
just a leaf hash and its audit path via `MerkleTree.rootFromAuditPath`, checked
against the value `buildProof` returns.
