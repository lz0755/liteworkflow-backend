CREATE TABLE infra.notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    actor_id UUID,
    workspace_id UUID,
    project_id UUID,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(500) NOT NULL,
    source_event_id UUID NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notifications_event_recipient_type
        UNIQUE (source_event_id, recipient_user_id, notification_type)
);

CREATE INDEX idx_notifications_recipient_created
    ON infra.notifications (recipient_user_id, created_at DESC, id);
CREATE INDEX idx_notifications_recipient_unread
    ON infra.notifications (recipient_user_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE infra.consumed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_infra_consumed_events_consumed_at ON infra.consumed_events (consumed_at);
