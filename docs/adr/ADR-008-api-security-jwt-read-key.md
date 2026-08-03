# ADR-008 — API security: Spring Security with JWT + read key

**Status:** Accepted (Implemented)  
**Date:** July–August 2026

## Context

The trust chain is only as strong as who may register sources and submit attestations. An open API would undermine the entire integrity model. A simple, operable authentication scheme is required for the MVP.

## Decision

Protect the API with **Spring Security**:
- Write/admin operations require a **JWT** (HS256, role claim: `INGEST` / `ADMIN`) issued by `/api/v1/auth/token`.
- Read operations accept a static **read key** (constant-time comparison).
- Stateless, no sessions; CORS configurable.
- Source registration is admin-only.

## Consequences

### Positive
- Role-scoped, stateless write authentication
- Low-privilege credential suitable for UI/proxy read access
- Simple to operate for a solo MVP
- Closes the open-registration spoofing gap

### Negative / Trade-offs
- Credentials are currently configured users (admin, ingest); a directory/OIDC provider is a later step
- HS256 is sufficient for the single-node MVP but should be revisited for multi-instance production

## Alternatives rejected
- No authentication – unacceptable for a trust system
- API keys only – lack standard role and expiry semantics for writes
- Full OIDC now – disproportionate for the MVP
