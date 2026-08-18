#!/bin/sh
# Destructive host proof for ADR-015: stop the primary container, wait for
# postgres-watch to promote the replica, then write through postgres-rw.
#
# Run from the compose host AFTER ci/check_postgres_replica.sh. Leaves the
# primary stopped and the replica as the writer. Rebuild a new standby by
# hand (volume + pg_basebackup) if you need HA again.
#
# Required: docker compose, running gridveritas stack.
set -eu

fail() { echo "FAIL: $1"; exit 1; }

net=$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' gridveritas-postgres-rw)
[ -n "$net" ] || net=gridveritas-net

psql_rw() {
  docker run --rm --network "$net" \
    -e PGPASSWORD=gridveritas \
    postgres:16-alpine \
    psql -h postgres-rw -U gridveritas -d gridveritas "$@"
}

echo "=== preflight: replica in recovery, proxy writable ==="
pre_r=$(docker compose exec -T postgres-replica \
  psql -U gridveritas -d gridveritas -tAc "SELECT pg_is_in_recovery();")
[ "$pre_r" = "t" ] || fail "replica was not in recovery before failover (got '$pre_r')"
pre_w=$(psql_rw -tAc "SELECT pg_is_in_recovery();")
[ "$pre_w" = "f" ] || fail "postgres-rw was not writable before failover (got '$pre_w')"

echo "=== stop primary (compose stop, so unless-stopped will not bring it back) ==="
docker compose stop postgres-primary

echo "=== wait for promote (watch default 15s + pg_promote) ==="
promoted=0
for i in $(seq 1 30); do
  rec=$(docker compose exec -T postgres-replica \
    psql -U gridveritas -d gridveritas -tAc "SELECT pg_is_in_recovery();" 2>/dev/null || echo "?")
  echo "  attempt $i/30 - replica pg_is_in_recovery=$rec"
  if [ "$rec" = "f" ]; then
    promoted=1
    break
  fi
  sleep 2
done
[ "$promoted" = "1" ] || fail "replica was never promoted"

echo "=== write through postgres-rw after promote ==="
# Retry: HAProxy may still have a dying primary connection for a couple of seconds.
wrote=0
for i in $(seq 1 15); do
  if psql_rw -v ON_ERROR_STOP=1 -c \
    "CREATE TABLE IF NOT EXISTS ha_failover_probe (at timestamptz NOT NULL);
     INSERT INTO ha_failover_probe VALUES (now());" >/dev/null 2>&1; then
    wrote=1
    break
  fi
  echo "  write attempt $i/15 failed; retrying"
  sleep 2
done
[ "$wrote" = "1" ] || fail "INSERT through postgres-rw failed after promote"

rows=$(psql_rw -tAc "SELECT count(*) FROM ha_failover_probe;")
echo "  ha_failover_probe rows=$rows"
[ "$rows" -ge 1 ] || fail "probe table is empty"

echo "Postgres failover host-proof passed. Primary is still stopped."
echo "Rebuild a standby before treating this stack as HA again."
