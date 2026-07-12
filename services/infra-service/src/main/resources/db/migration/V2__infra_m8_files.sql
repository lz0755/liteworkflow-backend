CREATE TABLE infra.stored_files (
    id UUID PRIMARY KEY,
    purpose VARCHAR(32) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_id UUID NOT NULL,
    workspace_id UUID,
    project_id UUID,
    issue_id UUID,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    extension VARCHAR(16) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256_hex VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    delete_attempts INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_stored_files_object_key_relative
        CHECK (object_key !~ '^/' AND strpos(object_key, chr(92)) = 0
            AND object_key !~ '(^|/)[.]{1,2}(/|$)')
);

CREATE INDEX idx_stored_files_scope_active
    ON infra.stored_files (purpose, scope_id, created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_stored_files_delete_queue
    ON infra.stored_files (deleted_at, id) WHERE status = 'PENDING_DELETE';

CREATE TABLE infra.file_outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_file_outbox_dispatch
    ON infra.file_outbox_events (status, next_attempt_at, created_at);
