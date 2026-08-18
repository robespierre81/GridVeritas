#!/bin/sh
# Polls GET /api/v1/cluster/instances (ADR-013) until at least $EXPECTED core
# instances report online, or times out. Piped via stdin into a throwaway
# container on gridveritas-net by the Jenkinsfile's "Instance count check"
# stage - same pattern as demo/tamper_demo.sh - so no workspace bind-mount is
# needed (which would not resolve under Docker-in-Docker Jenkins).
#
# Required env: ADMIN_PASSWORD, EXPECTED, BASE_URL (Traefik entrypoint)
set -e
apk add --no-cache curl jq >/dev/null 2>&1

ok=0
for i in $(seq 1 30); do
  token=$(curl -sS -X POST "$BASE_URL/api/v1/auth/token" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" \
      | jq -r .token 2>/dev/null || true)

  if [ -n "$token" ] && [ "$token" != "null" ]; then
    online=$(curl -fsS "$BASE_URL/api/v1/cluster/instances" \
        -H "Authorization: Bearer $token" | jq -r .onlineCount 2>/dev/null || echo 0)
    echo "  attempt $i/30 - online instances: $online (expected >= $EXPECTED)"
    if [ "$online" -ge "$EXPECTED" ] 2>/dev/null; then
      ok=1
      break
    fi
  else
    echo "  attempt $i/30 - could not obtain a token yet"
  fi
  sleep 5
done

if [ "$ok" != "1" ]; then
  echo "Expected at least $EXPECTED online core instance(s) via /api/v1/cluster/instances, never reached."
  exit 1
fi
echo "Instance count check passed: >= $EXPECTED core instance(s) online."
