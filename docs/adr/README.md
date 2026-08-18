# Architecture Decision Records (ADRs)

This directory contains the Architecture Decision Records for **GridVeritas**.

ADRs document significant architectural and technology choices. They follow a lightweight format and can be superseded by a new ADR when a decision changes.

| ID | Title | Status |
|----|-------|--------|
| [ADR-001](ADR-001-core-platform-spring-boot-postgresql.md) | Core platform: Spring Boot + PostgreSQL | Accepted |
| [ADR-002](ADR-002-edge-agents-go.md) | Edge agents: Go | Accepted |
| [ADR-003](ADR-003-ui-react.md) | UI: React | Accepted |
| [ADR-004](ADR-004-ai-anomaly-detection-ollama.md) | AI: statistical anomaly detection + Ollama audit assistant | Accepted |
| [ADR-005](ADR-005-deployment-docker-jenkins.md) | Deployment: Docker + Jenkins on Debian | Accepted |
| [ADR-006](ADR-006-provenance-merkle-rfc3161.md) | Provenance & anchoring: Merkle + RFC 3161, no blockchain | Accepted |
| [ADR-007](ADR-007-schema-flyway-append-only.md) | Schema via Flyway; runtime append-only via non-owner role | Accepted |
| [ADR-008](ADR-008-api-security-jwt-read-key.md) | API security: Spring Security with JWT + read key | Accepted |
| [ADR-009](ADR-009-anchor-token-verification.md) | Anchor token verification & trust-pinning | Accepted |
| [ADR-010](ADR-010-abuse-resistance-rate-limiting.md) | Abuse resistance: in-app rate limiting + body-size cap | Accepted |
| [ADR-011](ADR-011-enforced-append-only-role.md) | Enforced append-only via runtime non-owner role (migration) | Accepted |
| [ADR-012](ADR-012-test-coverage-and-load-testing.md) | Automated test coverage and performance/load testing | Accepted |
| [ADR-013](ADR-013-multi-instance-ha.md) | Multi-instance HA: Redis + Traefik + Postgres advisory locks | Accepted |
| [ADR-014](ADR-014-federation-signed-anchored-roots.md) | Federation of signed, anchored Merkle roots | Accepted |
| [ADR-015](ADR-015-postgres-streaming-replica.md) | Single-host PostgreSQL streaming replica + failover | Accepted |
| [ADR-016](ADR-016-der-mvs-reference-workflow.md) | DER MV&S reference workflow (FERC 2222) | Accepted |
| [ADR-017](ADR-017-public-reference-architecture.md) | Public reference-architecture publication package | Accepted |

Source: *GridVeritas Architecture Baseline v1.4* (August 2026). ODT numbering differs: ODT ADR-013 is federation (repo ADR-014); ODT ADR-014 is this M14 (repo ADR-016); ODT ADR-015 is M15 (repo ADR-017); ODT ADR-016 is database HA (repo ADR-015).
