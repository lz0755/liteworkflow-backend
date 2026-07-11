CREATE TABLE core.project_issue_counters (
    project_id UUID PRIMARY KEY REFERENCES core.projects(id),
    next_number BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_project_issue_counters_next CHECK (next_number > 0)
);

INSERT INTO core.project_issue_counters (project_id, next_number, updated_at)
SELECT id, 1, CURRENT_TIMESTAMP FROM core.projects;

-- M5 used unconditional uniqueness, which prevents reuse after a state is soft-deleted.
ALTER TABLE core.issue_states DROP CONSTRAINT uq_issue_states_project_name;
ALTER TABLE core.issue_states DROP CONSTRAINT uq_issue_states_project_position;
CREATE UNIQUE INDEX uq_issue_states_project_name_active
    ON core.issue_states (project_id, lower(name)) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_issue_states_project_position_active
    ON core.issue_states (project_id, position) WHERE status = 'ACTIVE';

CREATE TABLE core.issue_labels (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES core.projects(id),
    name VARCHAR(80) NOT NULL,
    color VARCHAR(7) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_issue_labels_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_issue_labels_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE UNIQUE INDEX uq_issue_labels_project_name_active
    ON core.issue_labels (project_id, lower(name)) WHERE status = 'ACTIVE';
CREATE INDEX idx_issue_labels_project
    ON core.issue_labels (project_id, status, lower(name), id);

CREATE TABLE core.issues (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES core.projects(id),
    issue_number BIGINT NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT,
    state_id UUID NOT NULL REFERENCES core.issue_states(id),
    client_request_id UUID,
    created_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    updated_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_issues_project_number UNIQUE (project_id, issue_number),
    CONSTRAINT uq_issues_project_request UNIQUE (project_id, client_request_id),
    CONSTRAINT ck_issues_number CHECK (issue_number > 0)
);

CREATE INDEX idx_issues_project_updated
    ON core.issues (project_id, updated_at DESC, id) WHERE deleted_at IS NULL;
CREATE INDEX idx_issues_project_state_updated
    ON core.issues (project_id, state_id, updated_at DESC, id) WHERE deleted_at IS NULL;
CREATE INDEX idx_issues_project_creator_updated
    ON core.issues (project_id, created_by, updated_at DESC, id) WHERE deleted_at IS NULL;
CREATE INDEX idx_issues_project_title_lower
    ON core.issues (project_id, lower(title)) WHERE deleted_at IS NULL;

CREATE TABLE core.issue_assignees (
    issue_id UUID NOT NULL REFERENCES core.issues(id),
    user_id UUID NOT NULL REFERENCES core.user_directory(user_id),
    assigned_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (issue_id, user_id)
);

CREATE INDEX idx_issue_assignees_user_issue ON core.issue_assignees (user_id, issue_id);

CREATE TABLE core.issue_label_relations (
    issue_id UUID NOT NULL REFERENCES core.issues(id),
    label_id UUID NOT NULL REFERENCES core.issue_labels(id),
    added_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    added_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (issue_id, label_id)
);

CREATE INDEX idx_issue_label_relations_label_issue
    ON core.issue_label_relations (label_id, issue_id);

ALTER TABLE core.activities ADD COLUMN project_id UUID REFERENCES core.projects(id);
CREATE INDEX idx_activities_project_created
    ON core.activities (project_id, created_at DESC) WHERE project_id IS NOT NULL;
