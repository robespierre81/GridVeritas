# ADR-003 — UI: React

**Status:** Accepted  
**Date:** July 2026

## Context

A management and audit console is required for viewing attestations, triggering verification, inspecting audit trails, and basic configuration. The first version should be delivered quickly and consume the existing REST API.

## Decision

Build the management/audit console as a **React** single-page application.

## Consequences

### Positive
- Fast path to a usable first console
- Large ecosystem and component libraries
- Easy consumption of the REST API

### Negative / Trade-offs
- Additional frontend technology to maintain
- Browser-based UI is not suitable for pure OT environments (accepted for the MVP)

## Alternatives considered
- Server-side rendered UI (Thymeleaf / HTMX) – slower iteration for interactive views
- Vue or Svelte – equally viable, React chosen for familiarity and speed
