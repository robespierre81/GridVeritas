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
- Client-IP correctness depends on the reverse proxy setting `X-Forwarded-For` — see
  the trust-boundary fix below; a deployment that puts a reverse proxy in front of
  core must add its address to `gridveritas.security.trusted-proxies` or every
  request will be keyed on the proxy's own IP instead of the real client's

## Update (August 2026): X-Forwarded-For trust boundary (REACH-1000)

`X-Forwarded-For` was originally trusted unconditionally, so any caller could get
a fresh rate-limit bucket on every request just by sending a different header
value — bypassing both the general limit and, more importantly, the stricter
`/auth/token` limit meant to slow credential brute-forcing. Fixed:
`RequestGuardFilter` now only honors `X-Forwarded-For` when the request's direct
peer address is in `gridveritas.security.trusted-proxies` (empty by default, so
`request.getRemoteAddr()` is always used until a deployment explicitly
configures its reverse proxy's address). Pinned by `RequestGuardFilterTest`
(both directions: untrusted peer → header ignored, trusted peer → header
honored) and load-tested for token-bucket correctness under real concurrency by
`RateLimiterConcurrencyTest` (see ADR-012).
