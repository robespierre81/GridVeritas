#!/bin/sh
# Promote the replica if the primary stays down, then freeze the old primary
# in HAProxy so a restart cannot split-brain. Single-host only (M11 residual).
set -eu

PRIMARY_HOST="${PRIMARY_HOST:-postgres-primary}"
REPLICA_HOST="${REPLICA_HOST:-postgres-replica}"
POSTGRES_USER="${POSTGRES_USER:-gridveritas}"
POSTGRES_DB="${POSTGRES_DB:-gridveritas}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-gridveritas}"
HAPROXY_SOCK="${HAPROXY_SOCK:-/ha/haproxy.sock}"
FLAG="${HA_FLAG:-/ha/failed-over}"
DOWN_SECONDS="${DOWN_SECONDS:-15}"

export PGPASSWORD="$POSTGRES_PASSWORD"

primary_ok() {
  pg_isready -h "$PRIMARY_HOST" -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1
}

replica_ok() {
  pg_isready -h "$REPLICA_HOST" -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1
}

freeze_old_primary() {
  if [ -S "$HAPROXY_SOCK" ]; then
    echo "set server pg_nodes/primary state maint" | socat - "$HAPROXY_SOCK" || true
    echo "set server pg_nodes/replica state ready" | socat - "$HAPROXY_SOCK" || true
  fi
}

already_failed_over() {
  [ -f "$FLAG" ]
}

echo "db-ha-watch started (promote after ${DOWN_SECONDS}s primary outage)"

if already_failed_over; then
  echo "failed-over flag present — keeping old primary out of the write path"
  freeze_old_primary
fi

missed=0
while true; do
  if already_failed_over; then
    freeze_old_primary
    sleep 5
    continue
  fi

  if primary_ok; then
    missed=0
    sleep 2
    continue
  fi

  missed=$((missed + 2))
  echo "primary not ready (${missed}s / ${DOWN_SECONDS}s)"
  if [ "$missed" -lt "$DOWN_SECONDS" ]; then
    sleep 2
    continue
  fi

  if ! replica_ok; then
    echo "replica also down — cannot promote"
    sleep 5
    continue
  fi

  echo "promoting replica at ${REPLICA_HOST}"
  if psql -h "$REPLICA_HOST" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT pg_promote();"; then
    date -Iseconds > "$FLAG"
    freeze_old_primary
    echo "failover complete; old primary frozen in haproxy"
  else
    echo "pg_promote failed; will retry"
  fi
  sleep 5
done
