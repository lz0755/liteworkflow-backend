package com.liteworkflow.infra.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NotificationProjectionServiceTest {

    @Autowired private NotificationProjectionService projectionService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ConsumedNotificationEventRepository consumedEventRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        notificationRepository.deleteAll();
        consumedEventRepository.deleteAll();
    }

    @Test
    void repeatedMqEventCreatesOnlyOneNotificationPerDeduplicatedRecipient() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event = new EventEnvelope<>(
                eventId,
                "comment.mentioned",
                1,
                Instant.now(),
                new EventScope(UUID.randomUUID(), UUID.randomUUID(), actorId),
                UUID.randomUUID(),
                objectMapper.valueToTree(Map.of(
                        "recipientUserIds", List.of(recipientId, recipientId, actorId),
                        "body", "this ignored field must never be copied")),
                Map.of());

        assertThat(projectionService.consume(event)).isTrue();
        assertThat(projectionService.consume(event)).isFalse();

        assertThat(notificationRepository.countBySourceEventId(eventId)).isOne();
        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getRecipientUserId()).isEqualTo(recipientId);
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.COMMENT_MENTION);
            assertThat(notification.getSourceEventId()).isEqualTo(eventId);
        });
        assertThat(consumedEventRepository.count()).isOne();
    }

    @Test
    void stateEventUsesTheSameEventIdIdempotencyBoundary() {
        UUID eventId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event = new EventEnvelope<>(
                eventId,
                "issue.state.changed",
                1,
                Instant.now(),
                new EventScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(),
                objectMapper.valueToTree(Map.of("recipientUserIds", List.of(recipientId))),
                Map.of());

        projectionService.consume(event);
        projectionService.consume(event);

        assertThat(notificationRepository.countBySourceEventId(eventId)).isOne();
        assertThat(notificationRepository.findAll()).singleElement()
                .extracting(Notification::getNotificationType)
                .isEqualTo(NotificationType.ISSUE_STATE_CHANGED);
    }
}
