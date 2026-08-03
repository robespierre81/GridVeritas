# ADR-002 — Edge agents: Go

**Status:** Accepted  
**Date:** July 2026

## Context

Attestation agents must run on gateways, RTUs, or virtual edge nodes. They need a small footprint, good concurrency, easy static binaries, and solid cryptographic libraries for Ed25519 signatures.

## Decision

Implement the attestation agents in **Go**.

## Consequences

### Positive
- Small static binaries ideal for edge/gateway deployment
- Excellent concurrency model
- Strong standard crypto libraries
- Simple cross-compilation

### Negative / Trade-offs
- Additional language in the overall stack (Java + Go + TypeScript/React)
- Team knowledge concentration on the solo developer

## Alternatives considered
- Rust (excellent performance and safety, but higher development friction for the MVP)
- Python (convenient for prototyping, but less suitable for constrained edge environments)
- Java (possible, but heavier runtime for edge devices)
