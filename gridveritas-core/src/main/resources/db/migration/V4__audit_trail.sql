-- GridVeritas verification & audit trail (V4). Append-only.

CREATE TABLE verification_events (
    id         uuid        NOT NULL,
    event_type varchar(20) NOT NULL,
    subject    varchar(128),
    result     varchar(40) NOT NULL,
    principal  varchar(64) NOT NULL,
    detail     text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_verification_events PRIMARY KEY (id)
);
CREATE INDEX idx_verification_events_created ON verification_events (created_at);

CREATE TABLE audit_log (
    id         uuid        NOT NULL,
    action     varchar(40) NOT NULL,
    principal  varchar(64) NOT NULL,
    target     varchar(128),
    detail     text,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);
CREATE INDEX idx_audit_log_created ON audit_log (created_at);
