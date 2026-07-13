CREATE TABLE rag.vector_store (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSON NOT NULL,
    embedding VECTOR(${embeddingDimensions}) NOT NULL
);

CREATE INDEX idx_rag_vector_scope
    ON rag.vector_store (
        (metadata->>'workspaceId'),
        (metadata->>'projectId'),
        (metadata->>'active'),
        (metadata->>'sourceType')
    );

CREATE TABLE rag.rag_index_jobs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    source_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_rag_index_job_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'DEAD')),
    CONSTRAINT ck_rag_index_job_retry_count CHECK (retry_count >= 0),
    CONSTRAINT uq_rag_index_job_source_version
        UNIQUE (source_type, source_id, source_version, event_id)
);

CREATE INDEX idx_rag_index_jobs_source
    ON rag.rag_index_jobs (source_type, source_id, source_version DESC);
CREATE INDEX idx_rag_index_jobs_retry
    ON rag.rag_index_jobs (status, lease_until, updated_at)
    WHERE status IN ('FAILED', 'PROCESSING');

CREATE TABLE rag.rag_source_heads (
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    project_id UUID NOT NULL,
    source_version BIGINT NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (source_type, source_id)
);

CREATE INDEX idx_rag_source_heads_scope
    ON rag.rag_source_heads (workspace_id, project_id, source_type);

CREATE TABLE rag.rag_index_chunks (
    vector_id UUID PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id UUID NOT NULL,
    source_version BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_rag_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT uq_rag_source_chunk
        UNIQUE (source_type, source_id, source_version, chunk_index)
);

CREATE INDEX idx_rag_index_chunks_active_source
    ON rag.rag_index_chunks (source_type, source_id, source_version DESC)
    WHERE active = TRUE;

-- Every active version contains chunk zero, so this also prevents two source
-- versions from becoming visible at the same time.
CREATE UNIQUE INDEX uq_rag_index_chunks_one_active_version
    ON rag.rag_index_chunks (source_type, source_id, chunk_index)
    WHERE active = TRUE;
