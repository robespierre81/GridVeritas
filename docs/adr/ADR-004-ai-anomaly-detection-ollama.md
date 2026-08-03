# ADR-004 — AI: statistical anomaly detection + Ollama audit assistant

**Status:** Accepted (Implemented)  
**Date:** July 2026 (updated August 2026)

## Context

The platform needs two AI-related capabilities:
1. Detection of anomalous attestation behaviour (sequence gaps, signature failures, source silence).
2. Natural-language assistance for audit and investigation questions.

LLMs are poorly suited for high-throughput per-event scoring, but useful for phrasing answers over retrieved facts.

## Decision

- Detect anomalies with **statistical methods** on attestation metadata inside the core (off the critical verification path).
- Provide natural-language audit assistance via a **local Ollama model**, grounded on facts retrieved from the database (RAG-lite) using Ollama’s HTTP API directly.
- The LLM is **never** used for per-event anomaly scoring.

## Consequences

### Positive
- Anomaly detection remains cheap, explainable, and non-blocking
- Audit assistant stays fully local (no cloud dependency)
- Stack remains Java-only for the core (no separate Python service required)

### Negative / Trade-offs
- RAG-lite without a vector store has limited semantic reach (accepted for MVP)
- Model quality depends on the chosen Ollama model and available RAM

## Notes
Earlier drafts mentioned “Ollama + LangChain”. The implementation uses direct Ollama HTTP calls with database-side retrieval. LangChain4j or a Python RAG service remain options if a vector store is added later.

## Later enhancements
- Semantic vector store over audit logs
- Moving-baseline / z-score anomaly methods
