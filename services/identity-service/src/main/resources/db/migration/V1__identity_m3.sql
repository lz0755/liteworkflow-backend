CREATE TABLE identity.identity_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_version BIGINT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_identity_users_email UNIQUE (email),
    CONSTRAINT ck_identity_users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_identity_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_identity_users_source_version CHECK (source_version >= 1)
);

CREATE TABLE identity.refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity.identity_users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_id UUID REFERENCES identity.refresh_tokens(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_refresh_tokens_user_active
    ON identity.refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE identity.password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity.identity_users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_password_reset_tokens_user_active
    ON identity.password_reset_tokens (user_id, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE TABLE identity.login_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES identity.identity_users(id) ON DELETE SET NULL,
    email_hash CHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    ip_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_login_logs_outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'RATE_LIMITED')),
    CONSTRAINT ck_login_logs_email_hash CHECK (email_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_login_logs_ip_hash CHECK (ip_hash IS NULL OR ip_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_login_logs_user_created_at ON identity.login_logs (user_id, created_at DESC);
CREATE INDEX idx_login_logs_email_created_at ON identity.login_logs (email_hash, created_at DESC);

CREATE TABLE identity.local_outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    exchange_name VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_identity_outbox_status CHECK (status IN ('PENDING', 'FAILED', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_identity_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_identity_outbox_recovery
    ON identity.local_outbox_events (status, next_retry_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');
