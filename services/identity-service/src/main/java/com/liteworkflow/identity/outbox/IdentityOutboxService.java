package com.liteworkflow.identity.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.identity.domain.IdentityUser;
import com.liteworkflow.identity.domain.LocalOutboxEvent;
import com.liteworkflow.identity.repository.LocalOutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityOutboxService {

    public static final String IDENTITY_EXCHANGE = "identity.event.exchange";
    public static final String USER_AGGREGATE_TYPE = "IDENTITY_USER";

    private final LocalOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public IdentityOutboxService(
            LocalOutboxEventRepository repository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    /** Writes only directory data. Password hashes, credentials, and audit fields never cross this boundary. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueUserEvent(String eventType, IdentityUser user, UUID actorId) {
        Instant now = clock.instant();
        IdentityUserEventPayload payload = new IdentityUserEventPayload(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(), user.getSourceVersion());
        EventEnvelope<IdentityUserEventPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                now,
                new EventScope(null, null, actorId),
                user.getId(),
                payload,
                Map.of());
        LocalOutboxEvent outboxEvent = new LocalOutboxEvent(
                envelope.eventId(),
                eventType,
                IDENTITY_EXCHANGE,
                eventType,
                USER_AGGREGATE_TYPE,
                user.getId(),
                serialize(envelope),
                now);
        repository.save(outboxEvent);
        applicationEventPublisher.publishEvent(new OutboxQueued(outboxEvent.getId()));
    }

    private JsonNode serialize(EventEnvelope<IdentityUserEventPayload> envelope) {
        return objectMapper.valueToTree(envelope);
    }
}
