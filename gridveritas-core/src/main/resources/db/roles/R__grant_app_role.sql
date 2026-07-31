-- Grants for the limited runtime role. Repeatable: re-applied whenever this file
-- changes (e.g. when a new table is added). Runs as the migration owner.
-- No-ops safely if the app role does not exist (single-user/dev setups).

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'gridveritas_app') THEN
        -- Append-only tables: read + insert only (no UPDATE / DELETE)
        GRANT SELECT, INSERT ON
            attestations,
            merkle_roots,
            merkle_leaves,
            anchors,
            anomaly_findings,
            verification_events,
            audit_log
            TO gridveritas_app;

        -- Mutable table: sources needs UPDATE (last_seen_at) and INSERT
        GRANT SELECT, INSERT, UPDATE ON sources TO gridveritas_app;
    END IF;
END $$;
