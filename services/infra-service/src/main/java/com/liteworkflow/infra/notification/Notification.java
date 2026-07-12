package com.liteworkflow.infra.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "infra",
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notifications_event_recipient_type",
                columnNames = {"source_event_id", "recipient_user_id", "notification_type"}))
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 64)
    private NotificationType notificationType;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            UUID id,
            UUID recipientUserId,
            NotificationType notificationType,
            String resourceType,
            UUID resourceId,
            UUID actorId,
            UUID workspaceId,
            UUID projectId,
            String title,
            String message,
            UUID sourceEventId,
            Instant createdAt) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.actorId = actorId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.title = title;
        this.message = message;
        this.sourceEventId = sourceEventId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public UUID getActorId() { return actorId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getSourceEventId() { return sourceEventId; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
