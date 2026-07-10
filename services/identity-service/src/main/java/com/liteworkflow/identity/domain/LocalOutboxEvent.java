package com.liteworkflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "local_outbox_events")
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

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
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
            String payloadJson,
            Instant now) {
        this.id = id;
        this.eventType = eventType;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
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
        return payloadJson;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
