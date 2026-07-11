package com.liteworkflow.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "core", name = "local_outbox_events")
public class LocalOutboxEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "exchange_name", nullable = false, length = 128)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 128)
    private String routingKey;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "actor_id")
    private UUID actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private JsonNode payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LocalOutboxEvent() {
    }

    public LocalOutboxEvent(
            UUID id,
            String eventType,
            String exchangeName,
            String routingKey,
            String aggregateType,
            UUID aggregateId,
            UUID workspaceId,
            UUID projectId,
            UUID actorId,
            JsonNode payloadJson,
            Instant now) {
        this.id = id;
        this.eventType = eventType;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.payloadJson = payloadJson;
        this.status = OutboxStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        nextRetryAt = null;
        lastError = null;
        updatedAt = now;
    }

    public void markFailed(Instant now, Instant nextRetryAt, String error, int maxRetries) {
        retryCount++;
        status = retryCount >= maxRetries ? OutboxStatus.DEAD : OutboxStatus.FAILED;
        this.nextRetryAt = status == OutboxStatus.DEAD ? null : nextRetryAt;
        lastError = error == null ? "publisher failure" : error.substring(0, Math.min(error.length(), 500));
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayloadJson() {
        return payloadJson.toString();
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
