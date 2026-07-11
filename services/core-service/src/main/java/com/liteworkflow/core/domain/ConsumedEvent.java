package com.liteworkflow.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "core", name = "consumed_events")
public class ConsumedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedEvent() {
    }

    public ConsumedEvent(UUID eventId, String eventType, Instant consumedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.consumedAt = consumedAt;
    }
}
