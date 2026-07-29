# GridVeritas Edge Agent (Go) – M5 starter

Lightweight agent that:

1. Generates (or loads) an **Ed25519** key pair
2. Registers as a **source** at the core (or uses `GRIDVERITAS_SOURCE_ID`)
3. Periodically builds a sample payload, **SHA-256** hashes it, **signs** the hash, and `POST`s an attestation

## Run locally (core on :18080)

```bash
go run ./cmd/agent
```

## Environment

| Variable | Default | Meaning |
|----------|---------|---------|
| `GRIDVERITAS_CORE_URL` | `http://localhost:18080` | Core base URL |
| `GRIDVERITAS_AGENT_NAME` | `edge-agent-01` | Source name when registering |
| `GRIDVERITAS_SOURCE_ID` | *(empty)* | Skip registration if set |
| `GRIDVERITAS_INTERVAL` | `15s` | Attestation interval |
| `GRIDVERITAS_KEY_FILE` | `agent.ed25519` | Private key path |

## Docker

```bash
docker build -t gridveritas/edge:local .
docker run --rm --network gridveritas-net \
  -e GRIDVERITAS_CORE_URL=http://gridveritas-core:8080 \
  gridveritas/edge:local
```

## Note

Core **verify** still only checks that a hash exists (MVP).  
Next improvement: core validates the Ed25519 signature using the source public key.
