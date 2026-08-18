# Load & performance tests

Two complementary tools, split by what needs real Ed25519 signing and what
doesn't. Both need a **running** gridveritas-core instance - `mvn test` never
runs either of these; they drive real HTTP load against a target you choose.

Never point either tool at a shared or production instance without the
operator's explicit go-ahead. Both are designed to trip the rate limiter
(`gridveritas.security.rate-limit.*`) once load exceeds its thresholds - that's
expected, not a bug, but it's still real load against a real system.

Wired into the Jenkinsfile as an opt-in stage (`RUN_LOAD_TEST` parameter,
default off) that runs both against the just-deployed, freshly-scaled stack
and archives their reports as build artifacts - see the "Load & performance
tests" stage.

## IngestLoadRunner (write path: ingest + verify)

`../src/test/java/com/gridveritas/core/loadtest/IngestLoadRunner.java` - a
plain Java class (no JUnit, never runs from `mvn test`). Mints a real Ed25519
key pair, registers it as a source, and signs and POSTs real attestations
concurrently, mixed with `POST /verify` and `GET /actuator/health` calls to
approximate real client traffic. Reports p50/p95/p99 latency and a 2xx/429/error
breakdown per endpoint, plus the count of distinct `X-Instance-Id` response
header values seen - against a multi-replica, Traefik-fronted deployment
(ADR-013), that's direct evidence load balancing actually happened, not an
assumption that it did.

```bash
mvn -Pload-test \
    -Dexec.args="<baseUrl> <adminPassword> <ingestPassword> [threads=8] [attestationsPerThread=200]" \
    test-compile exec:java
```

Example against a local instance running with the `test` Spring profile:

```bash
mvn -Pload-test -Dexec.args="http://localhost:8080 test-admin test-ingest 8 200" test-compile exec:java
```

Read path (token issuance, `/sources`, `/audit`) doesn't need per-request
signing, so it's covered by the k6 script instead of duplicating that logic
here.

## read-path.k6.js (read/auth path)

Requires [k6](https://k6.io/). Covers `/auth/token`, `/sources`, `/audit`, and
`/actuator/health` under configurable virtual-user load.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e ADMIN_PASSWORD=test-admin read-path.k6.js

# heavier, longer run:
k6 run --vus 20 --duration 30s -e BASE_URL=http://localhost:8080 -e ADMIN_PASSWORD=test-admin read-path.k6.js
```

Thresholds (`http_req_failed < 1%`, auth p95 < 1s) make k6 exit non-zero on
regression, so this is CI-able against a staging deployment - just not wired
into the default `mvn test`/`verify` build, deliberately: it needs a live
target and generates real load, neither of which belongs in a unit-test run.

## What isn't covered yet

Both tools exercise the HTTP/service/DB layers under load but not the
scheduled Merkle-sealing job (`MerkleService.sealNewLeaves`, every 60s by
default) or the RFC 3161 anchoring job under sustained high leaf-ingestion
rates - i.e. what happens when leaves accumulate faster than a sealing cycle
can drain them. Worth a follow-up if ingest throughput ever approaches
production-realistic numbers.
