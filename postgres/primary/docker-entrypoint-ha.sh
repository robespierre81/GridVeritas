#!/bin/sh
# Primary wrapper: enable WAL shipping on every start, and ensure the
# replicator role + pg_hba line exist even when the data volume already
# ran initdb (init scripts never re-run on an existing volume).
set -eu

PGDATA="${PGDATA:-/var/lib/postgresql/data}"
REPLICATOR_PASSWORD="${REPLICATOR_PASSWORD:-repl-change-me}"

ensure_replication() {
  until pg_isready -U "${POSTGRES_USER:-gridveritas}" -d "${POSTGRES_DB:-gridveritas}" >/dev/null 2>&1; do
    sleep 1
  done

  if [ -f "$PGDATA/pg_hba.conf" ] && ! grep -q "replication replicator" "$PGDATA/pg_hba.conf"; then
    # "host" covers encrypted and not; replica pg_basebackup uses no SSL.
    echo "host replication replicator 0.0.0.0/0 scram-sha-256" >> "$PGDATA/pg_hba.conf"
    echo "host replication replicator ::/0 scram-sha-256" >> "$PGDATA/pg_hba.conf"
  fi

  psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-gridveritas}" -d "${POSTGRES_DB:-gridveritas}" <<SQL
SELECT pg_reload_conf();
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'replicator') THEN
    CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '${REPLICATOR_PASSWORD}';
  ELSE
    ALTER ROLE replicator WITH REPLICATION LOGIN PASSWORD '${REPLICATOR_PASSWORD}';
  END IF;
END \$\$;
SELECT pg_create_physical_replication_slot('replica_1', true)
WHERE NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'replica_1');
SQL
}

ensure_replication &

exec docker-entrypoint.sh postgres \
  -c wal_level=replica \
  -c max_wal_senders=10 \
  -c max_replication_slots=10 \
  -c hot_standby=on \
  -c wal_keep_size=64MB
