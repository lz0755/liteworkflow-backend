CREATE TABLE infra.export_jobs (
    id UUID PRIMARY KEY,
    export_type VARCHAR(32) NOT NULL,
    export_format VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_export_jobs_type CHECK (export_type IN ('ISSUES')),
    CONSTRAINT ck_export_jobs_format CHECK (export_format IN ('CSV', 'XLSX')),
    CONSTRAINT ck_export_jobs_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_export_jobs_requester_created
    ON infra.export_jobs (requested_by, created_at DESC, id);
CREATE INDEX idx_export_jobs_project_created
    ON infra.export_jobs (project_id, created_at DESC, id);

CREATE TABLE infra.export_files (
    id UUID PRIMARY KEY,
    export_job_id UUID NOT NULL UNIQUE REFERENCES infra.export_jobs(id),
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    row_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_export_files_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_export_files_rows CHECK (row_count >= 0),
    CONSTRAINT ck_export_files_sha256 CHECK (char_length(sha256_hex) = 64),
    CONSTRAINT ck_export_files_object_key_relative
        CHECK (object_key !~ '^/' AND strpos(object_key, chr(92)) = 0
            AND object_key !~ '(^|/)[.]{1,2}(/|$)')
);

CREATE TABLE infra.export_outbox_events (
    id UUID PRIMARY KEY,
    export_job_id UUID NOT NULL REFERENCES infra.export_jobs(id),
    event_type VARCHAR(128) NOT NULL,
    exchange_name VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_export_outbox_status CHECK (status IN ('PENDING', 'FAILED', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_export_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_export_outbox_dispatch
    ON infra.export_outbox_events (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');
