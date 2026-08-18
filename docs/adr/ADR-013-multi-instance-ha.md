# ADR-013 — Multi-instance high availability: Redis + Traefik + Postgres advisory locks

**Status:** Accepted (Implemented)
**Date:** August 2026

## Context

gridveritas-core ran as exactly one instance: `docker-compose.yml` gave it a
fixed `container_name` and a fixed host port publish, both incompatible with
running more than one replica. Two pieces of application state were also
single-instance-only, already flagged as residual risk in ADR-010 and
`THREAT_MODEL.md`:

- `RateLimiter`'s token buckets lived in an in-memory `ConcurrentHashMap`. With
  N instances behind a load balancer, each instance enforces its own separate
  limit - the real effective limit becomes N× the configured one.
- `MerkleService.sealNewLeaves()` was a plain `@Scheduled` method with no
  coordination. With N instances, all N fire on the same schedule and would
  race to seal the same unsealed attestations.

## Decision

- **Redis** for shared rate-limit state (`RedisRateLimitStore`, behind the new
  `RateLimitStore` interface - `InMemoryRateLimitStore` remains the `test`
  profile's implementation, so `mvn test` needs no Redis). Chosen over an
  all-Postgres approach: rate-limit checks are on every request's hot path,
  and a purpose-built in-memory store avoids adding a DB round-trip to every
  single request the way a Postgres-backed bucket would.
- **Postgres advisory locks** (`pg_try_advisory_xact_lock`) for the
  Merkle-sealing job's leader election - only one instance actually seals per
  cycle. Postgres is already a hard dependency, and this is a well-established
  pattern for "only one instance runs this periodically" that needs no new
  infrastructure. Transaction-scoped (`_xact_`), so the lock releases
  automatically at commit/rollback - no manual unlock, no risk of leaking a
  lock across a pooled connection.
- **Traefik** as the load balancer in front of N core replicas, replacing
  core's direct host-port publish. Chosen over nginx: Traefik's Docker
  provider tracks replica start/stop/scale natively. A static nginx
  `proxy_pass` resolves a Docker Compose service name once at config-load time
  and caches it - it would keep talking to a single replica even when core is
  scaled, silently defeating the point.
- An `X-Instance-Id` response header (the container hostname) and a new
  `GET /api/v1/cluster/instances` endpoint, backed by a Postgres heartbeat
  table (`instance_heartbeat`, V6 migration) - lets anything (an operator, a
  load test, a Jenkins stage) ask "how many instances are online right now"
  without depending on Docker/Jenkins to know.

## Consequences

### Positive
- The rate limit is now one effective limit shared across all instances, not
  N separate ones.
- The sealing job can't produce duplicate or partial Merkle roots under
  multiple instances.
- `docker compose up -d --scale core=2` (or more) now works: Traefik picks up
  new replicas automatically, actively removes an unhealthy one from rotation
  (not just the one-time startup gate `depends_on` gives everything else), and
  the UI/edge/seed services all route through it rather than a fixed core
  address.
- "How many instances are online" is answerable at any time via
  `/api/v1/cluster/instances`, not only inferable from `docker compose ps`.
- Load-tested and verified: `IngestLoadRunner` reports the count of distinct
  `X-Instance-Id` values seen during a run - direct evidence load balancing
  happened, not an assumption.

### Negative / Trade-offs / Limitations
- New infrastructure dependency: Redis. (Traefik and the advisory-lock
  approach add no new infrastructure - Traefik replaces core's own port
  publish rather than adding a service most deployments didn't already need
  behind *some* reverse proxy, and Postgres was already required.)
- `RedisRateLimitStoreTest` and the Merkle leader-election test both need
  Docker (Testcontainers) and are skipped - not failed - on a Docker-less
  host; verified by running the suite on the deployment server instead (same
  approach used for `IngestLoadRunner`'s end-to-end verification, ADR-012).
- The instance-heartbeat table is not pruned - a long-lived deployment
  accumulates one row per instance that has ever started. Harmless at this
  scale; a follow-up if it ever matters.
- mTLS deployment mode (`docker-compose.mtls.yml`) is unchanged and remains
  single-instance - Traefik would need TCP-passthrough routing (not the
  simpler HTTP routing used here) to front an mTLS-terminating core without
  breaking client-certificate validation. Out of scope for this pass.

## Later enhancements
- TCP-passthrough Traefik routing for the mTLS deployment mode.
- Prune stale `instance_heartbeat` rows.
- A load scenario specifically for the Merkle-sealing job under sustained high
  ingestion rate with multiple instances (ADR-012 already flagged this as
  unexercised by either load tool).
