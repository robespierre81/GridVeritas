-- Instance registry (V6, ADR-013): lets any instance answer "how many
-- instances are online right now" via a shared table instead of relying on
-- Docker/Jenkins to know. instance_id is the container's own hostname
-- (Docker sets this to the container ID automatically).

CREATE TABLE instance_heartbeat (
    instance_id       varchar(64) NOT NULL,
    started_at        timestamp with time zone NOT NULL,
    last_heartbeat_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_instance_heartbeat PRIMARY KEY (instance_id)
);
CREATE INDEX idx_instance_heartbeat_last_beat ON instance_heartbeat (last_heartbeat_at);

GRANT SELECT, INSERT, UPDATE ON instance_heartbeat TO gridveritas_app;