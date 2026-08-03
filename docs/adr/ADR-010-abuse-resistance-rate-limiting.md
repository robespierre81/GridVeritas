# ADR-010 — Abuse resistance: in-app rate limiting + body-size cap

**Status:** Accepted (Implemented)  
**Date:** August 2026

## Context

The single-host MVP needs basic protection against denial-of-service and brute-force attacks without introducing extra infrastructure (e.g. Redis or an API gateway).

## Decision

Enforce, in a filter that runs **before** the security chain:
- A per-client (IP) **token-bucket rate limiter** (general limit + stricter limit on `/auth/token`)
- A **request-body size cap**

Exceeding the limits returns `429 Too Many Requests` (with `Retry-After`) or `413 Payload Too Large`.

## Consequences

### Positive
- Protects the public token endpoint and the rest of the API
- No additional infrastructure required for the MVP
- Simple to reason about

### Negative / Trade-offs / Limitations
- In-memory per instance (a shared store such as Redis is required for multi-instance)
- Body size cap is currently based on `Content-Length` (streaming/chunked bodies need a streaming cap)
- Client-IP correctness depends on the reverse proxy setting `X-Forwarded-For`
