package com.liteworkflow.infra.export;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "infra", name = "export_outbox_events")
public class ExportOutboxEvent {

    @Id
    private UUID id;

    @Column(name = "export_job_id", nullable = false, updatable = false)
    private UUID exportJobId;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    @Column(name = "exchange_name", nullable = false, length = 128, updatable = false)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 128, updatable = false)
    private String routingKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text", updatable = false)
    private String payloadJson;

    @Column(name = "trace_id", nullable = false, length = 128, updatable = false)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExportOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ExportOutboxEvent() {
    }

    public ExportOutboxEvent(
            UUID id,
            UUID exportJobId,
            String eventType,
            String exchangeName,
            String routingKey,
            String payloadJson,
            String traceId,
            Instant now) {
        this.id = id;
        this.exportJobId = exportJobId;
        this.eventType = eventType;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
        this.traceId = traceId;
        this.status = ExportOutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void markPublished(Instant now) {
        status = ExportOutboxStatus.PUBLISHED;
        nextAttemptAt = null;
        publishedAt = now;
    }

    public boolean markFailed(Instant now, Instant nextAttempt, int maxAttempts) {
        retryCount++;
        if (retryCount >= maxAttempts) {
            status = ExportOutboxStatus.DEAD;
            nextAttemptAt = null;
            return true;
        }
        status = ExportOutboxStatus.FAILED;
        nextAttemptAt = nextAttempt;
        return false;
    }

    public UUID getId() { return id; }
    public UUID getExportJobId() { return exportJobId; }
    public String getEventType() { return eventType; }
    public String getExchangeName() { return exchangeName; }
    public String getRoutingKey() { return routingKey; }
    public String getPayloadJson() { return payloadJson; }
    public String getTraceId() { return traceId; }
    public ExportOutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
}
