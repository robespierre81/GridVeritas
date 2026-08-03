# ADR-001 — Core platform: Spring Boot + PostgreSQL

**Status:** Accepted  
**Date:** July 2026

## Context

GridVeritas needs a reliable verification engine that can receive attestations, maintain Merkle provenance, store proofs, and expose REST APIs. The platform must be operable by a solo developer, easy to containerize, and suitable for a single-node MVP while remaining evolvable.

## Decision

Use **Spring Boot** for the verification engine and REST APIs, and **PostgreSQL** as the primary store for attestations, proofs, chains, audit logs, and configuration.

## Consequences

### Positive
- Mature ecosystem and excellent fit with the developer’s existing skills
- Strong observability via Spring Actuator
- Straightforward Docker packaging
- Reliable transactional storage for the append-only provenance model

### Negative / Trade-offs
- JVM startup and memory footprint are higher than a pure Go or Rust service
- Horizontal scaling and high-availability patterns are deferred to a later phase

## Alternatives considered
- Quarkus or Micronaut (faster startup, but less familiar)
- Node.js / NestJS (weaker fit for cryptographic and transactional workloads)
- Pure Go core (possible, but Spring Boot accelerates the first vertical slice)
