package com.liteworkflow.common.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void createsVersionedEventWithStableIdentifiers() {
        UUID aggregateId = UUID.randomUUID();

        EventEnvelope<String> event = EventEnvelope.create(
                "core.issue.created", 1, EventScope.global(), aggregateId, "payload");

        assertThat(event.eventId()).isNotNull();
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.metadata()).isEmpty();
    }

    @Test
    void rejectsUnversionedEvents() {
        assertThatThrownBy(() -> EventEnvelope.create(
                        "core.issue.created", 0, EventScope.global(), UUID.randomUUID(), "payload"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
