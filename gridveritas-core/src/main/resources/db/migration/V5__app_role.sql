-- Creates the limited runtime role and grants, as the migration owner.
-- Versioned (not repeatable) so it is guaranteed to run in order on every DB that
-- is not yet at v5 — independent of the Postgres initdb.d mechanism.
--
-- Idempotent via IF NOT EXISTS so it is safe even if the role was created by other
-- means. The password is read from a session GUC set by spring.flyway.init-sqls
-- (gridveritas.app_password); if that is empty it falls back to 'app-change-me'.

DO $$
DECLARE
    app_pw text := coalesce(nullif(current_setting('gridveritas.app_password', true), ''),
                            'app-change-me');
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gridveritas_app') THEN
        EXECUTE format('CREATE ROLE gridveritas_app LOGIN PASSWORD %L', app_pw);
    ELSE
        EXECUTE format('ALTER ROLE gridveritas_app LOGIN PASSWORD %L', app_pw);
    END IF;
END $$;

GRANT CONNECT ON DATABASE gridveritas TO gridveritas_app;
GRANT USAGE   ON SCHEMA public        TO gridveritas_app;

GRANT SELECT, INSERT ON
    attestations,
    merkle_roots,
    merkle_leaves,
    anchors,
    anomaly_findings,
    verification_events,
    audit_log
    TO gridveritas_app;

GRANT SELECT, INSERT ON attestations TO gridveritas_app;
GRANT SELECT, INSERT ON merkle_roots TO gridveritas_app;
GRANT SELECT, INSERT ON merkle_leaves TO gridveritas_app;
GRANT SELECT, INSERT, UPDATE ON sources TO gridveritas_app;          -- ← dieser fehlt aktuell