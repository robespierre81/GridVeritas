# ADR-012 — Automated test coverage and performance/load testing

**Status:** Accepted (Implemented)
**Date:** August 2026

## Context

Through M8, gridveritas-core's automated coverage was thin and concentrated in a
few crypto/service classes; the security filter chain, the ingest/verify and
Merkle-sealing services, and the RFC 3161 anchor verifier had none. That gap
was directly responsible for one previously-undetected regression
(`GridVeritasCoreApplicationTests` silently running Postgres-only Flyway
migrations against H2) and left REACH-1000 — an unauthenticated caller could
bypass rate limiting by spoofing `X-Forwarded-For` — both unfixed and
untested. Separately, there was no performance or load testing at all: no
tool had ever driven concurrent traffic against the ingest or read paths, so
throughput and latency under load were unknown.

## Decision

- Treat the security filter chain and the services backing the tamper-detection
  guarantee as **requiring** test coverage, not optional: `JwtAuthFilter`,
  `ReadKeyFilter`, `MtlsIngestFilter`, `RequestGuardFilter`, `TsaVerifier`,
  `AttestationService`, `MerkleService`.
- Where a class cannot be meaningfully tested without fixing a bug first (as
  with REACH-1000), fix before testing — a passing test against broken
  behavior just pins the bug as "correct."
- Add a concurrency test for `RateLimiter` specifically, since it is shared,
  mutable, and on every request's hot path; single-threaded unit tests do not
  exercise the property that actually matters in production.
- Add two purpose-built, **opt-in** performance/load tools, neither of which
  runs as part of `mvn test`/`verify`:
  - `IngestLoadRunner` (plain Java, real Ed25519 signing) for the write path
    (ingest + verify), invoked via a dedicated `load-test` Maven profile.
  - `read-path.k6.js` for the auth/read path, with pass/fail thresholds so it
    is usable in a staging CI job.

## Consequences

### Positive
- REACH-1000 is fixed: `X-Forwarded-For` is now only trusted from a
  configured `gridveritas.security.trusted-proxies` allow-list (empty by
  default), pinned by `RequestGuardFilterTest`.
- The RFC 3161 anchor-verification logic (`TsaVerifier`) — message imprint,
  signature, trust-pinning both directions — is exercised against real,
  self-minted BouncyCastle timestamp tokens, not mocks.
- The Merkle sealing/chaining and inclusion-proof logic is verified end to
  end, including an independent reconstruction of the root hash from a leaf +
  audit path (`MerkleTree.rootFromAuditPath`) — the actual guarantee an
  external verifier relies on.
- `RateLimiterConcurrencyTest` proves the token bucket does not over-admit
  under real multi-threaded contention.
- The load tools give a real, reproducible baseline instead of an unknown:
  a local run drove 22 mixed ingest/verify/health requests with 100% success
  and p95 ingest latency ~85ms (see `load-tests/README.md` for the current
  numbers and how to reproduce them against a target of your own).
- Suite grew from 24 to 73 tests as of this ADR.

### Negative / Trade-offs / Limitations
- Coverage is still incomplete: the REST controller layer (`AttestationController`,
  `AnomalyController`, `AuditAssistantController`, `AuthController`,
  `StatsController`) and `AnchorService`/`AuditService`/`AuditAssistantService`
  remain untested (tracked in REACH-1007).
- `TsaVerifier`'s Extended-Key-Usage rejection path has no direct test:
  BouncyCastle's own RFC 3161 stack enforces the same constraint (and a
  mandatory ESS signing-certificate attribute) at both token generation and
  parse time, so producing a token that reaches the check with a
  non-conformant EKU would mean reimplementing internals of
  `TimeStampTokenGenerator` for one assertion. Documented as a known gap
  rather than silently skipped.
- Neither load tool has been run at anything resembling production
  scale/hardware, and the scheduled Merkle-sealing job under sustained high
  ingestion rate is not covered by either — noted as a follow-up.
- The load tools are deliberately excluded from CI's default build (they
  need a live target and generate real load); `read-path.k6.js`'s thresholds
  make it usable in a staging pipeline specifically, not the unit-test build.

## Later enhancements
- Controller-level (`@WebMvcTest`) and remaining service coverage (REACH-1007).
- Wire `read-path.k6.js` into a staging deployment pipeline.
- A load scenario for the Merkle-sealing job under sustained high ingest rate.
