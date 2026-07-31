#!/usr/bin/env bash
#
# GridVeritas M7 demo (M8-aware): authenticates, provokes anomalies, queries the
# audit assistant. Requires curl and python3.
#
# Fast run: start core with low intervals, e.g.:
#   ANOMALY_INTERVAL_MS=10000 ANOMALY_SILENCE_MS=30000 MERKLE_SEAL_INTERVAL_MS=10000
#
# Usage:
#   API=http://localhost:18080/api/v1 ADMIN_PASSWORD=admin-change-me ./demo/anomaly_assistant_demo.sh
#
set -euo pipefail
API="${API:-http://localhost:18080/api/v1}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin-change-me}"
WAIT="${WAIT:-15}"

json() { python3 -m json.tool; }

echo "==> 0) Obtain an admin token"
TOKEN=$(curl -fsS -X POST "$API/auth/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
AUTH="Authorization: Bearer $TOKEN"
echo "    token acquired"

echo "==> 1) Create a demo source"
SRC=$(curl -fsS -X POST "$API/sources" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"demo-anomaly-source","publicKey":"ZGVtbw=="}')
SID=$(printf '%s' "$SRC" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "    source id = $SID"

submit() {
  local seq="$1" hash
  hash=$(printf 'payload-%s-%s' "$seq" "${RANDOM}" | sha256sum | cut -d' ' -f1)
  curl -fsS -X POST "$API/attestations" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"sourceId\":\"$SID\",\"payloadHash\":\"$hash\",\"timestampEpochMillis\":$(date +%s)000,\"sequenceNr\":$seq,\"signature\":\"aW52YWxpZC1zaWc=\"}" \
    >/dev/null
  echo "    submitted seq=$seq (deliberately invalid signature)"
}

echo "==> 2) Submit seq 1,2,3 then 7,8,9  ->  gap 4-6  +  100% invalid-signature ratio"
for s in 1 2 3 7 8 9; do submit "$s"; done

echo "==> 3) Wait ${WAIT}s for the detector to run"
sleep "$WAIT"

echo "==> 4) Anomalies for this source"
curl -fsS -H "$AUTH" "$API/anomalies?sourceId=$SID" | json

echo "==> 5) Ask the audit assistant"
curl -fsS -X POST "$API/audit/ask" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"question":"Which sources have anomalies and what types? Note any invalid-signature problems."}' | json

echo
echo "==> SOURCE_SILENCE: stop submitting for this source; after the silence threshold"
echo "    a SOURCE_SILENCE finding appears on re-check:"
echo "    curl -s -H \"$AUTH\" \"$API/anomalies?sourceId=$SID\" | python3 -m json.tool"
