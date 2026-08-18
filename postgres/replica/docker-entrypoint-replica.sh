#!/bin/sh
# Clone the primary once, then run as a hot standby.
set -eu

PGDATA="${PGDATA:-/var/lib/postgresql/data}"
PRIMARY_HOST="${PRIMARY_HOST:-postgres-primary}"
REPLICATOR_PASSWORD="${REPLICATOR_PASSWORD:-repl-change-me}"

echo "waiting for primary ${PRIMARY_HOST}..."
until pg_isready -h "$PRIMARY_HOST" -U "${POSTGRES_USER:-gridveritas}" >/dev/null 2>&1; do
  sleep 2
done

if [ ! -f "$PGDATA/PG_VERSION" ]; then
  echo "cloning primary via pg_basebackup..."
  rm -rf "${PGDATA:?}/"*
  n=0
  until PGPASSWORD="$REPLICATOR_PASSWORD" pg_basebackup \
    -h "$PRIMARY_HOST" \
    -U replicator \
    -D "$PGDATA" \
    -Fp -Xs -P -R \
    -S replica_1; do
    n=$((n + 1))
    if [ "$n" -ge 15 ]; then
      echo "pg_basebackup failed after 15 attempts"
      exit 1
    fi
    echo "pg_basebackup retry $n/15..."
    rm -rf "${PGDATA:?}/"*
    sleep 2
  done
  echo "clone complete"
fi

exec docker-entrypoint.sh postgres \
  -c hot_standby=on \
  -c primary_conninfo="host=${PRIMARY_HOST} user=replicator password=${REPLICATOR_PASSWORD} application_name=replica_1"
