CREATE TABLE core.projects (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES core.workspaces(id),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_projects_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX idx_projects_workspace ON core.projects (workspace_id, status, lower(name), id);

CREATE TABLE core.project_members (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES core.projects(id),
    user_id UUID NOT NULL REFERENCES core.user_directory(user_id),
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    added_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    joined_at TIMESTAMPTZ NOT NULL,
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT ck_project_members_role CHECK (role IN ('PROJECT_ADMIN', 'MEMBER', 'VIEWER')),
    CONSTRAINT ck_project_members_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);

CREATE INDEX idx_project_members_user ON core.project_members (user_id, status);
CREATE INDEX idx_project_members_project ON core.project_members (project_id, status, role);

CREATE TABLE core.issue_states (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES core.projects(id),
    name VARCHAR(80) NOT NULL,
    category VARCHAR(32) NOT NULL,
    position INT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_issue_states_project_name UNIQUE (project_id, name),
    CONSTRAINT uq_issue_states_project_position UNIQUE (project_id, position),
    CONSTRAINT ck_issue_states_category CHECK (category IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT ck_issue_states_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_issue_states_position CHECK (position >= 0)
);

CREATE UNIQUE INDEX uq_issue_states_project_default
    ON core.issue_states (project_id) WHERE is_default AND status = 'ACTIVE';
CREATE INDEX idx_issue_states_project ON core.issue_states (project_id, status, position);
