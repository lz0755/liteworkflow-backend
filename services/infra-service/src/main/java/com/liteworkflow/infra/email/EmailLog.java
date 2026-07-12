package com.liteworkflow.infra.email;

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
        name = "email_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_email_logs_event_recipient_template",
                columnNames = {"source_event_id", "recipient_user_id", "template_code"}))
public class EmailLog {

    @Id
    private UUID id;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Column(name = "template_code", nullable = false, updatable = false, length = 64)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EmailDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailLog() {
    }

    public EmailLog(
            UUID id,
            UUID sourceEventId,
            UUID recipientUserId,
            String templateCode,
            Instant now) {
        this.id = id;
        this.sourceEventId = sourceEventId;
        this.recipientUserId = recipientUserId;
        this.templateCode = templateCode;
        this.status = EmailDeliveryStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        attemptCount++;
        status = EmailDeliveryStatus.SENT;
        lastErrorCode = null;
        sentAt = now;
        updatedAt = now;
    }

    public void markFailed(Instant now, String errorCode, boolean terminal) {
        attemptCount++;
        status = terminal ? EmailDeliveryStatus.DEAD : EmailDeliveryStatus.RETRYING;
        lastErrorCode = errorCode;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public EmailDeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
