# GridVeritas — Threat Model (Baseline)

Status: Working draft, aligned with Architecture Baseline v0.2 and the implemented
core (M1–M8). This document is a required deliverable before any pilot with a
regulated entity (see Architecture Baseline 3.5 / ADR-007).

It records the assets, trust boundaries, and a STRIDE-style analysis mapping each
threat to the control that is **already implemented** versus the **residual risk /
planned control**. It is deliberately honest about what is not yet mitigated.

Related decisions: ADR-006 (Merkle + RFC 3161 anchoring, not a blockchain),
ADR-007 (Flyway-managed schema + two-role append-only), ADR-008 (API auth: JWT +
read key), ADR-009 (anchor-token verification & trust-pinning), ADR-010 (rate
limiting + body-size cap), ADR-011 (enforced append-only via a runtime non-owner
role, provisioned by a versioned migration), ADR-012 (automated test coverage
and performance/load testing), ADR-013 (multi-instance HA: Redis + Traefik +
Postgres advisory locks).

---

## 1. Scope

In scope: the GridVeritas core (Spring Boot), the Go edge agent, the PostgreSQL
store, the external RFC 3161 timestamping authority (TSA), the local Ollama audit
assistant, and the React UI. Out of scope: physical security of field devices,
the security of the operator's wider network, and formal certification.

## 2. System overview & trust boundaries

```
[ field device ] -- signs --> [ Go edge agent ] --TB1--> [ Spring Boot core ] --TB2--> [ PostgreSQL ]
                                                              |  \--TB3--> [ RFC 3161 TSA ] (external)
                                                              |  \--TB4--> [ Ollama ] (local LLM)
                                   [ React UI ] --TB5--------/
```

- **TB1** edge → core: network between an attesting agent and the core (may cross an electronic security perimeter). mTLS available (opt-in); ingest can require a client certificate.
- **TB2** core → database: the core trusts the DB; a privileged DB actor is a distinct threat. The runtime connection uses a limited non-owner role.
- **TB3** core → TSA: outbound to an external, independent party; only a hash is sent.
- **TB4** core → Ollama: local model server; untrusted output must be treated as untrusted.
- **TB5** UI/API clients → core: authenticated (JWT for writes, read key for reads).

## 3. Assets

1. **Data integrity & provenance** — the guarantee that a reading was not altered between source and decision point. Primary asset.
2. **Chain-of-custody records** — attestations, Merkle roots, anchors, audit findings, verification/audit trail.
3. **Source private keys** — held at the edge; compromise allows forging authentic attestations.
4. **Credentials** — JWT signing secret, the read key, DB role passwords, TLS keys.
5. **Availability** — the verification API and ingest path.
6. **Confidentiality** — limited: GridVeritas stores hashes and metadata, not raw payloads, by design.

## 4. Actors

- **External network attacker** — can observe/modify traffic on TB1/TB5 if not protected.
- **Malicious or compromised platform operator** — has full DB and host access. The adversary that anchoring specifically targets.
- **Compromised edge agent** — holds a valid signing key.
- **Curious insider / read-only observer** — wants data they shouldn't see.
- **Denial-of-service actor** — wants to exhaust the ingest path or the assistant.

## 5. STRIDE analysis

### Spoofing (identity)
- **Threat:** an attacker submits attestations impersonating a legitimate source.
  - *Implemented:* every attestation is Ed25519-signed at the source over a canonical
    message binding payload hash + source id + sequence + timestamp; the core verifies
    against the registered public key (`Ed25519Verifier`).
  - *Implemented (M8, ADR-008):* source registration (`POST /sources`) now requires an
    `ADMIN` JWT; ingest (`POST /attestations`) requires `INGEST` or `ADMIN`. Open
    registration is closed.
  - *Residual:* credentials are configured MVP users (admin, ingest); move to a directory /
    OIDC provider for the pilot.

### Tampering (integrity)
- **Threat (in transit):** modify a reading between edge and core.
  - *Implemented:* source signature makes in-transit payload tampering detectable regardless of transport.
  - *Implemented (M8):* opt-in mTLS on TB1 — the core serves HTTPS with `client-auth: want`,
    and ingest additionally requires a validated client certificate (enforced at the app layer).
  - *Residual:* mTLS is opt-in; enable it (and TLS on TB5, typically terminated at the reverse proxy) in the pilot deployment.
- **Threat (at rest, by operator):** rewrite stored history.
  - *Implemented:* hash-linked Merkle provenance; roots chained via `prev_root_hash`;
    roots externally anchored to an RFC 3161 TSA — altering a stored attestation changes
    the recomputed root, which no longer matches the anchored token. This is the core
    guarantee against a malicious operator.
  - *Implemented (M8, ADR-009):* the stored TSA token is verified — message imprint against
    the root, CMS signature against the embedded signer certificate, and the timestamping
    EKU — at acquisition (an unverifiable token is never stored) and on `/proof`. With a
    configured TSA CA, the signer is trust-pinned via PKIX; otherwise integrity is verified
    but not trust-pinned (logged). Verified by `TsaVerifierTest` and `MerkleServiceTest`
    (ADR-012), including an independent verifier's root reconstruction from a leaf + audit
    path — not just that the code runs without throwing.
  - *Implemented (M8, ADR-011):* append-only is enforced at runtime — the app connects as a
    non-owner role with `SELECT`/`INSERT` only on append-only tables; the role and grants are
    provisioned by a versioned migration.
  - *Implemented (August 2026, ADR-013):* the sealing job (`MerkleService.sealNewLeaves`) is
    now safe to run on multiple instances — a Postgres advisory lock
    (`pg_try_advisory_xact_lock`) ensures only one instance actually seals per cycle.
    Without this, multiple instances racing to seal the same unsealed attestations could
    produce duplicate or partial Merkle roots, which would itself have looked like
    tampering to an independent verifier.
  - *Residual:* TSA revocation (CRL/OCSP) checking is deferred; anchoring cadence bounds the
    undetected-tampering window (open domain question, Baseline 6).

### Repudiation
- **Threat:** a party denies having produced a reading, or the operator denies altering data.
  - *Implemented:* source signatures attribute data to a key; anchored Merkle roots give
    non-repudiable, time-stamped evidence of the recorded state.
  - *Implemented (M8):* a persisted, append-only trail — `verification_events` (who verified
    what, and the result) and `audit_log` (source created, token issued/denied, anchor
    created) — each recording the authenticated principal. Exposed via `GET /audit` and
    `GET /audit/verifications`, and fed to the audit assistant.
  - *Residual:* the trail is protected by append-only grants, not yet independently anchored;
    anchoring the audit trail itself is a possible later enhancement.

### Information disclosure
- **Threat:** exposure of sensitive data.
  - *Implemented:* by design the system stores payload **hashes**, not raw payloads; only the
    root hash is sent to the external TSA, so the TSA learns nothing about the data.
  - *Implemented (M8, ADR-008):* reads require a credential (JWT or read key); actuator is
    restricted except `/health`. Error responses are clean JSON (no stack traces).
  - *Residual:* the read key is a low-privilege shared secret suitable for a UI/proxy; treat
    read access as gated at the perimeter. Secrets are still plaintext env in compose (dev
    acceptable; use a secret manager for the pilot).
  - *LLM-specific:* the audit assistant is grounded only on retrieved facts and instructed not
    to invent data; still, treat model output as untrusted and never execute it.

### Denial of service
- **Threat:** exhaust ingest, sealing, anchoring, or the assistant.
  - *Implemented:* sealing/anchoring/detection run off the critical path on bounded batches;
    the assistant fails soft when Ollama is unavailable.
  - *Implemented (M8, ADR-010):* a per-client (IP) token-bucket rate limiter — general plus a
    stricter limit on `/auth/token` — returning `429` with `Retry-After`, and a request-body
    size cap returning `413`, enforced before the security chain.
  - *Implemented (August 2026, ADR-010):* `X-Forwarded-For` is only trusted from a configured
    reverse-proxy address (`gridveritas.security.trusted-proxies`); previously any caller could
    spoof the header to get a fresh bucket per request, bypassing both limits (REACH-1000).
    Verified under real thread contention, not just single-threaded logic, by
    `RateLimiterConcurrencyTest` (ADR-012).
  - *Implemented (August 2026, ADR-013):* the rate limiter's state is now Redis-backed
    (`RedisRateLimitStore`), so multiple gridveritas-core instances behind Traefik enforce
    one shared effective limit per client instead of N separate ones (one per instance's
    own in-memory map, as before). Traefik load-balances across replicas and actively
    excludes an unhealthy one, rather than a static reverse-proxy config that would
    silently keep sending all traffic to a single instance.
  - *Residual:* the body cap is Content-Length based (streaming/chunked bodies need a
    streaming cap); correct per-client limiting still requires the proxy to be listed in
    `trusted-proxies` (an unconfigured deployment falls back to the connecting socket
    address, which is safe but wrong behind an unlisted proxy).

### Elevation of privilege
- **Threat:** an application-level actor gains DB owner or host privileges.
  - *Implemented (M8, ADR-011):* the runtime app role (`gridveritas_app`) is a non-owner with
    `SELECT`/`INSERT` on append-only tables and `UPDATE` on `sources` only; migrations run as
    the owner. The role and grants are provisioned by a versioned migration, so enforcement is
    on by default in the standard stack (no reliance on init-time hooks).
  - *Residual:* the owner role used for migrations is a superuser in the dev compose; narrow it
    (a dedicated `CREATEROLE` role, not superuser) and use real secrets for the pilot.

## 6. Key residual risks — prioritized for the pilot

The highest-severity gaps from earlier iterations (open registration / unauthenticated API,
and the unverified anchor token) are now closed (M8, ADR-008/009). Remaining items:

1. **Secrets management** — replace plaintext env (JWT secret, read key, DB and TLS
   passwords) with a secret manager; narrow the migration owner role.
2. **Certificate revocation** — add CRL/OCSP checking for the TSA and mTLS certificates.
3. **Enable transport encryption in deployment** — turn on mTLS (TB1) and TLS at the proxy (TB5).
4. **Move to a real identity provider** — replace the two configured users with a directory / OIDC.
5. **Multi-instance concerns** — mostly closed (ADR-013): shared rate-limit state (Redis),
   Merkle-sealing leader election (Postgres advisory lock), and load balancing (Traefik) are
   all implemented. mTLS deployment mode remains single-instance (would need TCP-passthrough
   routing); full production HA (rolling deploys, multi-AZ, etc.) is a later phase (ADR-005).
6. **Anchoring cadence** — choose the sealing/anchoring interval that bounds the acceptable
   undetected-tampering window (open domain question, Baseline 6); consider a second TSA.

## 7. Assumptions & out of scope

- Edge devices are assumed to protect their private keys (hardware-backed where available).
- Physical security and the operator's wider network security are out of scope.
- Formal NERC CIP / IEC 62443 certification is out of scope for the first release; the goal
  is to produce evidence and controls useful in that environment.
- Anchoring depends on the availability and long-term trustworthiness of the chosen TSA
  (Baseline 9.1 risk); using more than one TSA mitigates single-TSA dependence.
