# ADR-005 — Deployment: Docker + Jenkins on Debian

**Status:** Accepted  
**Date:** July 2026

## Context

The project is executed by a solo developer on a Debian host. The runtime must be simple to operate for development and pilot, while still supporting reproducible builds.

## Decision

- Package all services as **Docker** containers.
- Use **Jenkins** for CI/CD.
- Run the stack on a **single Debian host** (Docker Compose).

## Consequences

### Positive
- Matches available environment and skills
- Reproducible builds and easy local reproduction
- Clear path to later orchestration

### Negative / Trade-offs
- Single-host, single-PostgreSQL baseline is **not highly available**
- Does not meet the 99.9 % availability design target (accepted limitation for the current phase)
- Kubernetes and multi-instance deployment are deferred until the core is stable

## Known limitation
High-availability options (database replication/failover, multi-instance services, Kubernetes) are explicitly out of scope for the MVP and pilot packaging.
