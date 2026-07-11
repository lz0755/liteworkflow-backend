CREATE TABLE core.issue_comments (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES core.issues(id),
    author_id UUID NOT NULL REFERENCES core.user_directory(user_id),
    body TEXT,
    updated_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    deleted_by UUID REFERENCES core.user_directory(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_issue_comments_body CHECK (deleted_at IS NOT NULL OR body IS NOT NULL)
);

CREATE INDEX idx_issue_comments_issue_created
    ON core.issue_comments (issue_id, created_at, id) WHERE deleted_at IS NULL;

CREATE TABLE core.issue_mentions (
    comment_id UUID NOT NULL REFERENCES core.issue_comments(id),
    user_id UUID NOT NULL REFERENCES core.user_directory(user_id),
    mentioned_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX idx_issue_mentions_user_comment ON core.issue_mentions (user_id, comment_id);

CREATE TABLE core.issue_subscribers (
    issue_id UUID NOT NULL REFERENCES core.issues(id),
    user_id UUID NOT NULL REFERENCES core.user_directory(user_id),
    subscribed_by UUID NOT NULL REFERENCES core.user_directory(user_id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (issue_id, user_id)
);

CREATE INDEX idx_issue_subscribers_user_issue ON core.issue_subscribers (user_id, issue_id);
