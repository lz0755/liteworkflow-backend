CREATE TABLE infra.email_logs (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_email_logs_event_recipient_template
        UNIQUE (source_event_id, recipient_user_id, template_code),
    CONSTRAINT ck_email_logs_status
        CHECK (status IN ('PENDING', 'RETRYING', 'SENT', 'DEAD')),
    CONSTRAINT ck_email_logs_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_email_logs_recipient_created
    ON infra.email_logs (recipient_user_id, created_at DESC, id);
CREATE INDEX idx_email_logs_status_updated
    ON infra.email_logs (status, updated_at, id);

CREATE TABLE infra.email_outbox (
    id UUID PRIMARY KEY,
    email_log_id UUID NOT NULL UNIQUE REFERENCES infra.email_logs (id) ON DELETE CASCADE,
    source_event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    recipient_user_id UUID NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    workspace_id UUID,
    project_id UUID,
    status VARCHAR(24) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_email_outbox_status
        CHECK (status IN ('PENDING', 'RETRYING', 'SENT', 'DEAD')),
    CONSTRAINT ck_email_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_email_outbox_dispatch
    ON infra.email_outbox (status, next_attempt_at, created_at, id)
    WHERE status IN ('PENDING', 'RETRYING');
