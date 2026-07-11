CREATE TABLE core.user_directory (
    user_id UUID PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL,
    email_display VARCHAR(320) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    avatar_file_id UUID,
    account_status VARCHAR(32) NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_user_directory_normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_user_directory_status CHECK (account_status IN ('ACTIVE', 'DISABLED', 'DELETED')),
    CONSTRAINT ck_user_directory_source_version CHECK (source_version >= 0)
);

CREATE INDEX idx_user_directory_display_name_lower ON core.user_directory (lower(display_name));
CREATE INDEX idx_user_directory_email_lower ON core.user_directory (lower(normalized_email));
CREATE INDEX idx_user_directory_status ON core.user_directory (account_status);

CREATE TABLE core.user_profiles (
    user_id UUID PRIMARY KEY REFERENCES core.user_directory(user_id) ON DELETE CASCADE,
    bio VARCHAR(500),
    job_title VARCHAR(120),
    timezone VARCHAR(64),
    locale VARCHAR(32),
    avatar_file_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE core.workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_workspaces_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX idx_workspaces_created_by ON core.workspaces (created_by, status);

CREATE TABLE core.workspace_members (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES core.workspaces(id),
    user_id UUID NOT NULL REFERENCES core.user_directory(user_id),
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    added_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    joined_at TIMESTAMPTZ NOT NULL,
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_workspace_members_workspace_user UNIQUE (workspace_id, user_id),
    CONSTRAINT ck_workspace_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    CONSTRAINT ck_workspace_members_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);

CREATE INDEX idx_workspace_members_user ON core.workspace_members (user_id, status);
CREATE INDEX idx_workspace_members_workspace ON core.workspace_members (workspace_id, status, role);

CREATE TABLE core.activities (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES core.workspaces(id),
    actor_id UUID NOT NULL REFERENCES core.user_directory(user_id),
    activity_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_activities_workspace_created ON core.activities (workspace_id, created_at DESC);

CREATE TABLE core.local_outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    exchange_name VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    workspace_id UUID,
    project_id UUID,
    actor_id UUID,
    payload_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_core_outbox_status CHECK (status IN ('PENDING', 'FAILED', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_core_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_core_outbox_recovery ON core.local_outbox_events (status, next_retry_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE core.consumed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_consumed_events_consumed_at ON core.consumed_events (consumed_at);
