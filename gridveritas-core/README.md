# GridVeritas Core – Spring Boot MVP

First implementation slice of the GridVeritas verification engine.

## What is included

- Spring Boot 3.3 + Java 21
- PostgreSQL (or H2 for quick local start)
- Domain: `Source`, `Attestation`
- REST API:
  - `POST /api/v1/attestations`
  - `GET  /api/v1/attestations/{id}`
  - `GET  /api/v1/attestations?sourceId=...`
  - `POST /api/v1/verify`
  - `GET  /api/v1/sources`
  - `POST /api/v1/sources` (dev helper)
  - `GET  /api/v1/audit` (placeholder)
  - `GET  /api/v1/cluster/instances` (ADR-013 - how many instances are online)
  - `GET  /api/v1/federation/info` and `GET /api/v1/federation/roots` (M13 publish)
  - `GET/POST /api/v1/federation/peers`, `POST .../fetch`, `GET /peer-roots`, `POST /verify`
  - `GET/POST /api/v1/aggregators`, `/resources`, `/settlements` (M14 DER MV&S reference)
  - `GET /api/v1/resources/{id}/attestations`, `GET /api/v1/settlements/{id}`, `GET /settlements/mapping`

## Open in Apache NetBeans

1. **File → Open Project…**
2. Navigate to this folder (`gridveritas-core`)
3. NetBeans should detect it as a Maven project
4. Right-click the project → **Build** (or **Clean and Build**)
5. Right-click → **Run**

If NetBeans does not detect Maven automatically:
- Right-click the project → **Properties** → **Sources** and confirm the Source/Binary Format is JDK 21
- Make sure a Maven installation is configured under **Tools → Options → Java → Maven**

## Quick start without PostgreSQL (recommended first run)

```bash
# From the project root
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

This uses an in-memory H2 database.  
H2 console (when using `dev` profile): http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:mem:gridveritas`

## With PostgreSQL

1. Create database and user:

```sql
CREATE USER gridveritas WITH PASSWORD 'gridveritas';
CREATE DATABASE gridveritas OWNER gridveritas;
```

2. Start the application (default profile uses `application.yml`):

```bash
mvn spring-boot:run
```

## Example requests

### 1. Create a source

```bash
curl -X POST http://localhost:8080/api/v1/sources \
  -H "Content-Type: application/json" \
  -d '{"name":"demo-gateway-01","publicKey":"demo-public-key"}'
```

Copy the returned `id`.

### 2. Submit an attestation

```bash
curl -X POST http://localhost:8080/api/v1/attestations \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "<paste-source-id-here>",
    "payloadHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "timestamp": "2026-07-27T15:00:00Z",
    "sequenceNr": 1,
    "signature": "demo-signature"
  }'
```

### 3. Verify

```bash
curl -X POST http://localhost:8080/api/v1/verify \
  -H "Content-Type: application/json" \
  -d '{"payloadHash":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}'
```

## Testing

```bash
mvn test
```

79 tests (H2, `test` profile — see `application-test.yml`), covering:

- **Crypto**: `Ed25519Verifier`, `MerkleTree`, `TsaVerifier` (real self-minted
  RFC 3161 tokens via BouncyCastle, not mocks)
- **Security**: `JwtService`, `JwtAuthFilter`, `ReadKeyFilter`, `MtlsIngestFilter`,
  `RequestGuardFilter` (rate limit + body-size cap, including the
  `X-Forwarded-For` trust-boundary fix, REACH-1000), and a concurrency test
  proving the rate limiter's token bucket holds up under real thread
  contention, not just single-threaded logic
- **Services**: `AttestationService` (signature verification, replay
  rejection, tampered-metadata detection), `MerkleService` (sealing/chaining,
  inclusion-proof round-trip, tamper detection, anchor status),
  `AnomalyDetectionService`, `InstanceRegistryService` (ADR-013)

Two more need Docker (Testcontainers) and are skipped - not failed - without
it: `RedisRateLimitStoreTest` and `MerkleSealLeaderElectionTest`, verifying
the multi-instance HA mechanisms in ADR-013. Run `mvn test` on a Docker-capable
host (or Jenkins) to exercise them.

Still untested: the REST controller layer and `AnchorService`/`AuditService`/
`AuditAssistantService` — tracked in Reacher as REACH-1007. See
[ADR-012](../docs/adr/ADR-012-test-coverage-and-load-testing.md) for the full
rationale and [THREAT_MODEL.md](../docs/THREAT_MODEL.md) for which threats each
test actually closes.

## Performance & load testing

Two opt-in tools — neither runs as part of `mvn test`/`verify`, since both need
a live target and generate real load. See
[`load-tests/README.md`](load-tests/README.md) for full instructions.

```bash
# Write path (real Ed25519-signed ingest + verify), against a running instance:
mvn -Pload-test -Dexec.args="http://localhost:8080 <adminPassword> <ingestPassword>" \
    test-compile exec:java

# Read/auth path (needs k6):
k6 run -e BASE_URL=http://localhost:8080 -e ADMIN_PASSWORD=<adminPassword> load-tests/read-path.k6.js
```

Wired into Jenkins as an opt-in stage (`RUN_LOAD_TEST` parameter, off by
default) that runs both against the just-deployed stack and archives the
reports as build artifacts.

## High availability / multi-instance (ADR-013)

`core` has no fixed container name or host port, so it can run as multiple
replicas behind Traefik:

```bash
docker compose up -d --scale core=2
curl http://localhost:18080/api/v1/cluster/instances -H "Authorization: Bearer <token>"
```

Shared state that would otherwise break under multiple instances is handled:
rate limiting is Redis-backed (`RedisRateLimitStore`), and the Merkle-sealing
job elects a leader via a Postgres advisory lock
(`gridveritas.merkle.leader-election-enabled`, on by default outside the
`test` profile) so only one instance seals per cycle. See
[ADR-013](../docs/adr/ADR-013-multi-instance-ha.md).

## Next steps after this MVP works

1. Add real signature verification (Ed25519 / ECDSA)
2. Add hash-chain / Merkle linking
3. Build React UI against these endpoints
4. Create the first Go edge agent
5. Add Docker Compose + Jenkins pipeline refinements

## Project structure

```
com.gridveritas.core
├── GridVeritasCoreApplication.java
├── domain/          Source, Attestation
├── repository/
├── service/         AttestationService
├── web/             AttestationController + DTOs
└── config/          (empty for now)
```

## Docker

### Build the image

```bash
docker build -t gridveritas/core:0.1.0 -t gridveritas/core:latest .
```

### Run (with H2 / dev profile for quick test)

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  gridveritas/core:latest
```

### Run against an external PostgreSQL

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/gridveritas \
  -e SPRING_DATASOURCE_USERNAME=gridveritas \
  -e SPRING_DATASOURCE_PASSWORD=gridveritas \
  gridveritas/core:latest
```

Health endpoint: `http://localhost:8080/actuator/health`

## Jenkins

A first `Jenkinsfile` is included in the project root.

Typical stages:
1. Checkout
2. Maven build (`mvn clean package`)
3. Unit tests
4. Docker image build (`gridveritas/core:<BUILD_NUMBER>` + `latest`)
5. Archive the JAR

**Notes for your Jenkins instance:**
- The agent must have Docker available (Docker-in-Docker or mounted Docker socket).
- Adjust the `JAVA_HOME` / JDK tool name in the `Jenkinsfile` to match your Jenkins JDK installation (currently set to `JDK21`).
- The “Push Image” stage is commented out – enable it when you have a registry and credentials ready.

To use it: create a Multibranch Pipeline or Pipeline job that points to this repository and the `Jenkinsfile`.
