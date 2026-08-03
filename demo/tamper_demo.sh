#!/usr/bin/env bash
#
# GridVeritas live tamper demonstration.
# Shows that altering a stored record AFTER it was sealed & anchored is provably
# detected — even as the database owner. Requires: a running stack with seeded
# data (./gridveritas-edge seeder or the demo seeder), curl, python3, docker.
#
# Usage:
#   API=http://localhost:18080/api/v1 ADMIN_PASSWORD=admin-change-me ./demo/tamper_demo.sh
#
set -euo pipefail
API="${API:-http://localhost:18080/api/v1}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin-change-me}"
PG_CONTAINER="${PG_CONTAINER:-gridveritas-postgres}"
PG_USER="${PG_USER:-gridveritas}"
PG_DB="${PG_DB:-gridveritas}"

pyget() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

echo "==> Authenticate (admin)"
TOKEN=$(curl -fsS -X POST "$API/auth/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" | pyget 'd["token"]')
AUTH="Authorization: Bearer $TOKEN"

echo "==> Find a source and an attestation"
SID=$(curl -fsS -H "$AUTH" "$API/sources" | pyget 'd[0]["id"] if d else ""')
[ -n "$SID" ] || { echo "No sources. Seed demo data first (docker compose --profile seed run --rm seed)."; exit 1; }
AID=$(curl -fsS -H "$AUTH" "$API/attestations?sourceId=$SID" | pyget 'd[0]["id"] if d else ""')
[ -n "$AID" ] || { echo "No attestations for source $SID."; exit 1; }
echo "    source=$SID"
echo "    attestation=$AID"

echo "==> Wait until it is sealed into a Merkle root"
for i in $(seq 1 30); do
  STATUS=$(curl -fsS -H "$AUTH" "$API/attestations/$AID/proof" | pyget 'd.get("status","")')
  [ "$STATUS" = "SEALED" ] && break
  echo "    status=$STATUS ... waiting ($i)"; sleep 5
done
[ "$STATUS" = "SEALED" ] || { echo "Not sealed yet — lower MERKLE_SEAL_INTERVAL_MS and retry."; exit 1; }

echo
echo "==> BEFORE tampering — the proof:"
curl -fsS -H "$AUTH" "$API/attestations/$AID/proof" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print("    status          :",d.get("status"));print("    provenanceIntact:",d.get("provenanceIntact"));print("    anchored        :",d.get("anchored"));print("    anchorSigValid  :",d.get("anchorSignatureValid"));print("    leaf (anchored) :",d.get("leafHash"))'

echo
echo "==> Now act as a MALICIOUS OPERATOR: edit the stored record directly in the DB"
NEWHASH=$(printf 'tampered-%s' "$RANDOM" | sha256sum | cut -d' ' -f1)
docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -v ON_ERROR_STOP=1 \
  -c "UPDATE attestations SET payload_hash = '$NEWHASH' WHERE id = '$AID';" >/dev/null
echo "    payload_hash changed to $NEWHASH  (silently, with full DB access)"

echo
echo "==> AFTER tampering — the proof re-checks the stored record against the anchored leaf:"
curl -fsS -H "$AUTH" "$API/attestations/$AID/proof" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print("    status          :",d.get("status"));print("    provenanceIntact:",d.get("provenanceIntact"),"  <-- tampering detected");print("    currentLeaf     :",d.get("currentLeaf"));print("    leaf (anchored) :",d.get("leafHash"));print("    message         :",d.get("message"))'

echo
echo "==> Independent signature re-check also fails for the altered payload:"
curl -fsS -X POST "$API/verify" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"payloadHash\":\"$NEWHASH\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print("    valid  :",d.get("valid"));print("    message:",d.get("message"))'

echo
echo "Point made: with full database access, the change was still caught — because the"
echo "leaf was externally anchored. Re-seed or 'docker compose down -v' to reset the demo."
