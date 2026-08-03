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

Source: *GridVeritas Architecture Baseline v0.2 / v0.3* (July–August 2026)
