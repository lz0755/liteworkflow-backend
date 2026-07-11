package com.liteworkflow.infra.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "infra", name = "consumed_events")
public class ConsumedNotificationEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedNotificationEvent() {
    }

    public ConsumedNotificationEvent(UUID eventId, String eventType, Instant consumedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.consumedAt = consumedAt;
    }
}
