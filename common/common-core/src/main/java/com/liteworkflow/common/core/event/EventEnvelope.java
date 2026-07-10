package com.liteworkflow.common.core.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        EventScope scope,
        UUID aggregateId,
        T payload,
        Map<String, String> metadata) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static <T> EventEnvelope<T> create(
            String eventType, int version, EventScope scope, UUID aggregateId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(), eventType, version, Instant.now(), scope, aggregateId, payload, Map.of());
    }
}
