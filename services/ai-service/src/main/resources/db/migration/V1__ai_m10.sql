CREATE TABLE ai.ai_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID,
    project_id UUID,
    operation VARCHAR(64) NOT NULL,
    title VARCHAR(240) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_conversation_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_ai_conversations_user_updated
    ON ai.ai_conversations (user_id, updated_at DESC, id);
CREATE INDEX idx_ai_conversations_project_updated
    ON ai.ai_conversations (project_id, updated_at DESC, id);

CREATE TABLE ai.ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai.ai_conversations(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_message_role CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT')),
    CONSTRAINT ck_ai_message_token_count CHECK (token_count >= 0)
);

CREATE INDEX idx_ai_messages_conversation_created
    ON ai.ai_messages (conversation_id, created_at, id);

CREATE TABLE ai.ai_usage_logs (
    request_id UUID PRIMARY KEY,
    trace_id VARCHAR(128),
    user_id UUID NOT NULL,
    workspace_id UUID,
    project_id UUID,
    conversation_id UUID REFERENCES ai.ai_conversations(id) ON DELETE SET NULL,
    operation VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    chat_model VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(128),
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL,
    success BOOLEAN NOT NULL,
    error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_usage_input_tokens CHECK (input_tokens >= 0),
    CONSTRAINT ck_ai_usage_output_tokens CHECK (output_tokens >= 0),
    CONSTRAINT ck_ai_usage_total_tokens CHECK (total_tokens >= 0),
    CONSTRAINT ck_ai_usage_latency CHECK (latency_ms >= 0)
);

CREATE INDEX idx_ai_usage_user_created
    ON ai.ai_usage_logs (user_id, created_at DESC);
CREATE INDEX idx_ai_usage_workspace_created
    ON ai.ai_usage_logs (workspace_id, created_at DESC);

CREATE TABLE ai.ai_daily_quotas (
    usage_date DATE NOT NULL,
    user_id UUID NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    token_count BIGINT NOT NULL DEFAULT 0,
    reserved_tokens BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (usage_date, user_id),
    CONSTRAINT ck_ai_daily_requests CHECK (request_count >= 0),
    CONSTRAINT ck_ai_daily_tokens CHECK (token_count >= 0),
    CONSTRAINT ck_ai_daily_reserved CHECK (reserved_tokens >= 0)
);
