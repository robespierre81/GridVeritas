#!/bin/sh
# Prove M13 publish + peer-fetch on a live stack (ADR-014).
#
# Piped via stdin into a throwaway alpine container on gridveritas-net
# (same DinD-safe pattern as ci/check_instance_count.sh). No bind-mount.
#
# This is a loopback peer: this operator fetches its own published document
# through the load balancer. That is the "second URL" the host proof needs
# without standing up a second GridVeritas. Signature verification is still
# real — the fetch client must accept the published public key and store
# peer_roots.signature_valid=true for any sealed root.
#
# Required env: ADMIN_PASSWORD, BASE_URL (e.g. http://gridveritas-traefik)
set -e
apk add --no-cache curl jq >/dev/null 2>&1

fail() { echo "FAIL: $1"; exit 1; }

echo "=== federation /info (permitAll) ==="
info=$(curl -fsS "$BASE_URL/api/v1/federation/info") || fail "GET /federation/info"
op=$(echo "$info" | jq -r .operatorId)
pub=$(echo "$info" | jq -r .publicKey)
echo "  operatorId=$op"
[ -n "$op" ] && [ "$op" != "null" ] || fail "operatorId missing"
[ -n "$pub" ] && [ "$pub" != "null" ] || fail "publicKey missing"

echo "=== federation /roots (permitAll) ==="
# Sealing is on a 60s schedule; poll so a just-seeded stack can produce a root.
roots_json=""
root_count=0
for i in $(seq 1 24); do
  roots_json=$(curl -fsS "$BASE_URL/api/v1/federation/roots?limit=20") || fail "GET /federation/roots"
  echo "$roots_json" | jq -e .operatorId >/dev/null || fail "/roots is not a PublishedBundle"
  echo "$roots_json" | jq -e --arg p "$pub" '.publicKey == $p' >/dev/null \
    || fail "/roots publicKey does not match /info"
  root_count=$(echo "$roots_json" | jq '.roots | length')
  echo "  attempt $i/24 - published roots: $root_count"
  if [ "$root_count" -gt 0 ]; then
    break
  fi
  sleep 5
done

echo "=== admin token ==="
token=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/token" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | jq -r .token)
[ -n "$token" ] && [ "$token" != "null" ] || fail "could not obtain admin token"

peer_name="host-proof-self-$(date +%s)"
echo "=== register loopback peer $peer_name -> $BASE_URL ==="
peer=$(curl -fsS -X POST "$BASE_URL/api/v1/federation/peers" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$peer_name\",\"baseUrl\":\"$BASE_URL\",\"publicKey\":\"$pub\"}")
peer_id=$(echo "$peer" | jq -r .id)
[ -n "$peer_id" ] && [ "$peer_id" != "null" ] || fail "POST /federation/peers did not return an id"

echo "=== fetch peer $peer_id ==="
report=$(curl -fsS -X POST "$BASE_URL/api/v1/federation/peers/$peer_id/fetch" \
    -H "Authorization: Bearer $token")
echo "$report" | jq .
err=$(echo "$report" | jq -r '.error // empty')
[ -z "$err" ] || fail "fetch error: $err"

echo "=== stored peer roots ==="
stored=$(curl -fsS "$BASE_URL/api/v1/federation/peer-roots?limit=50" \
    -H "Authorization: Bearer $token")
stored_n=$(echo "$stored" | jq 'length')
echo "  stored=$stored_n"
if [ "$root_count" -gt 0 ]; then
  [ "$stored_n" -gt 0 ] || fail "published $root_count roots but stored none"
  valid=$(echo "$stored" | jq '[.[] | select(.signatureValid == true)] | length')
  echo "  signatureValid=$valid"
  [ "$valid" -gt 0 ] || fail "no stored peer root has signatureValid=true"
else
  echo "  no sealed roots yet; publish+fetch path still exercised"
fi

echo "Federation host-proof passed (publish + loopback fetch)."
