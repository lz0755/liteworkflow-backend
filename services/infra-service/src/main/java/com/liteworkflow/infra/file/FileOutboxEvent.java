package com.liteworkflow.infra.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "infra", name = "file_outbox_events")
public class FileOutboxEvent {
    public enum Status { PENDING, PUBLISHED, FAILED }
    @Id private UUID id;
    @Column(name = "event_type", nullable = false, length = 128) private String eventType;
    @Column(name = "routing_key", nullable = false, length = 128) private String routingKey;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text") private String payloadJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;

    protected FileOutboxEvent() {}
    public FileOutboxEvent(UUID id, String eventType, String routingKey, String payloadJson, Instant now) {
        this.id = id; this.eventType = eventType; this.routingKey = routingKey; this.payloadJson = payloadJson;
        this.status = Status.PENDING; this.nextAttemptAt = now; this.createdAt = now;
    }
    public void published(Instant now) { status = Status.PUBLISHED; publishedAt = now; }
    public void failed(Instant next) { status = Status.FAILED; retryCount++; nextAttemptAt = next; }
    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getRoutingKey() { return routingKey; }
    public String getPayloadJson() { return payloadJson; }
    public Status getStatus() { return status; }
}
