package com.liteworkflow.infra.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "infra", name = "email_outbox")
public class EmailOutboxJob {

    @Id
    private UUID id;

    @Column(name = "email_log_id", nullable = false, unique = true, updatable = false)
    private UUID emailLogId;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "trace_id", nullable = false, updatable = false, length = 128)
    private String traceId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Column(name = "template_code", nullable = false, updatable = false, length = 64)
    private String templateCode;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmailDeliveryStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailOutboxJob() {
    }

    public EmailOutboxJob(
            UUID id,
            UUID emailLogId,
            UUID sourceEventId,
            String eventType,
            String traceId,
            UUID recipientUserId,
            String templateCode,
            String resourceType,
            UUID resourceId,
            UUID workspaceId,
            UUID projectId,
            Instant now) {
        this.id = id;
        this.emailLogId = emailLogId;
        this.sourceEventId = sourceEventId;
        this.eventType = eventType;
        this.traceId = traceId;
        this.recipientUserId = recipientUserId;
        this.templateCode = templateCode;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.status = EmailDeliveryStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        status = EmailDeliveryStatus.SENT;
        sentAt = now;
        nextAttemptAt = now;
        updatedAt = now;
    }

    public boolean markFailed(Instant now, int maxAttempts, Duration retryDelay) {
        retryCount++;
        boolean terminal = retryCount >= maxAttempts;
        status = terminal ? EmailDeliveryStatus.DEAD : EmailDeliveryStatus.RETRYING;
        nextAttemptAt = terminal ? now : now.plus(retryDelay.multipliedBy(backoffMultiplier()));
        updatedAt = now;
        return terminal;
    }

    private long backoffMultiplier() {
        return 1L << Math.min(Math.max(retryCount - 1, 0), 6);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmailLogId() {
        return emailLogId;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getTraceId() {
        return traceId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public EmailDeliveryStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
