#!/bin/sh
# Prove the streaming replica is actually a hot standby (ADR-015).
# Run from Jenkins / the compose host (needs docker compose).
set -eu

fail() { echo "FAIL: $1"; exit 1; }

echo "=== wait for postgres-replica to accept connections ==="
ready=0
for i in $(seq 1 30); do
  if docker compose exec -T postgres-replica \
      pg_isready -U gridveritas -d gridveritas >/dev/null 2>&1; then
    ready=1
    break
  fi
  echo "  attempt $i/30 - replica not ready"
  sleep 2
done
[ "$ready" = "1" ] || fail "postgres-replica never accepted connections"

echo "=== postgres-replica is in recovery ==="
recovery=$(docker compose exec -T postgres-replica \
  psql -U gridveritas -d gridveritas -tAc "SELECT pg_is_in_recovery();")
echo "  pg_is_in_recovery=$recovery"
[ "$recovery" = "t" ] || fail "replica is not in recovery (got '$recovery')"

echo "=== postgres-rw reaches a writable primary ==="
net=$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' gridveritas-postgres-rw)
[ -n "$net" ] || net=gridveritas-net
probe=$(docker run --rm --network "$net" \
  -e PGPASSWORD=gridveritas \
  postgres:16-alpine \
  psql -h postgres-rw -U gridveritas -d gridveritas -tAc "SELECT pg_is_in_recovery();")
echo "  postgres-rw pg_is_in_recovery=$probe"
[ "$probe" = "f" ] || fail "postgres-rw is not pointing at a writable primary (got '$probe')"

echo "Postgres replica host-proof passed (standby + writable proxy)."
