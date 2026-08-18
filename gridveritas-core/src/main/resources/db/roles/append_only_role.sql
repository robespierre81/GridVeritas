-- Append-only application role for GridVeritas.
-- Run ONCE as a superuser or the database owner. This is NOT a Flyway migration:
-- roles and privileges are cluster-level and environment-specific.
--
-- WHY TWO ROLES: a table OWNER always bypasses table privileges, so append-only
-- can only be enforced against a NON-OWNER role. Flyway migrations run as the
-- owner (full DDL); the application connects as the limited role below.
--
-- To activate: run this script, then start the app with
--   DB_APP_USER=gridveritas_app  DB_APP_PASSWORD=...   (runtime, limited)
--   DB_OWNER_USER=gridveritas     DB_OWNER_PASSWORD=... (owns tables, runs Flyway)

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gridveritas_app') THEN
        CREATE ROLE gridveritas_app LOGIN PASSWORD 'CHANGE_ME';
    END IF;
END$$;
GRANT CONNECT ON DATABASE gridveritas TO gridveritas_app;
GRANT USAGE   ON SCHEMA public        TO gridveritas_app;

-- Heartbeat table for multi-instance HA (needs UPDATE for last_heartbeat_at)
GRANT SELECT, INSERT, UPDATE ON instance_heartbeat TO gridveritas_app;


-- Append-only tables: SELECT + INSERT only (no UPDATE, no DELETE)
GRANT SELECT, INSERT ON attestations  TO gridveritas_app;
GRANT SELECT, INSERT ON merkle_roots  TO gridveritas_app;
GRANT SELECT, INSERT ON merkle_leaves TO gridveritas_app;

-- Mutable table: sources needs UPDATE (last_seen_at) and INSERT
GRANT SELECT, INSERT, UPDATE ON sources TO gridveritas_app;

-- Same restriction for any future tables created by the owner in this schema
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT ON TABLES TO gridveritas_app;

-- NOTE: append-only here is defense-in-depth. It stops accidental and normal-path
-- mutation, but a privileged operator can still bypass it. The real guarantee
-- against operator tampering is the Merkle chain + external anchor (M6).
