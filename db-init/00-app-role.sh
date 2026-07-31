#!/bin/sh
# Runs once on first database initialization (empty data dir).
# Creates the limited runtime role. Table-level grants are applied afterwards by
# the repeatable Flyway migration R__grant_app_role.sql (run as the owner).
set -e

psql -v ON_ERROR_STOP=1 \
     -v app_pw="${APP_DB_PASSWORD:-app-change-me}" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE gridveritas_app LOGIN PASSWORD :'app_pw';
GRANT CONNECT ON DATABASE gridveritas TO gridveritas_app;
GRANT USAGE ON SCHEMA public TO gridveritas_app;
EOSQL

echo "gridveritas_app role created."
