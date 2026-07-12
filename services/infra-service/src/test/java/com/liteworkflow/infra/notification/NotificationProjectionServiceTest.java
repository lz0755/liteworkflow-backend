package com.liteworkflow.infra.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.event.EventScope;
import com.liteworkflow.infra.email.EmailLogRepository;
import com.liteworkflow.infra.email.EmailOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NotificationProjectionServiceTest {

    @Autowired private NotificationProjectionService projectionService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ConsumedNotificationEventRepository consumedEventRepository;
    @Autowired private EmailLogRepository emailLogRepository;
    @Autowired private EmailOutboxRepository emailOutboxRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        emailOutboxRepository.deleteAll();
        emailLogRepository.deleteAll();
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
        assertThat(emailLogRepository.count()).isOne();
        assertThat(emailOutboxRepository.count()).isOne();
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

    @Test
    void memberEventsNotifyTheAddedMemberAndIgnoreSelfAdds() {
        UUID workspaceEventId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID workspaceMember = UUID.randomUUID();
        projectionService.consume(event(
                workspaceEventId,
                "workspace.member.added",
                new EventScope(workspaceId, null, UUID.randomUUID()),
                UUID.randomUUID(),
                Map.of("memberUserId", workspaceMember, "role", "MEMBER")));

        UUID projectEventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        projectionService.consume(event(
                projectEventId,
                "project.member.added",
                new EventScope(workspaceId, projectId, actorId),
                UUID.randomUUID(),
                Map.of("memberUserId", actorId, "role", "PROJECT_ADMIN")));

        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getRecipientUserId()).isEqualTo(workspaceMember);
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.WORKSPACE_MEMBER_ADDED);
            assertThat(notification.getResourceId()).isEqualTo(workspaceId);
        });
        assertThat(consumedEventRepository.count()).isEqualTo(2);
        assertThat(emailOutboxRepository.count()).isOne();
    }

    @Test
    void issueEventsNotifyOnlyNewAssignees() {
        UUID actorId = UUID.randomUUID();
        UUID existingAssignee = UUID.randomUUID();
        UUID newAssignee = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        projectionService.consume(event(
                UUID.randomUUID(),
                "issue.assignees.changed",
                new EventScope(UUID.randomUUID(), UUID.randomUUID(), actorId),
                issueId,
                Map.of(
                        "assigneeIds", Set.of(existingAssignee, newAssignee, actorId),
                        "previousAssigneeIds", Set.of(existingAssignee))));

        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getRecipientUserId()).isEqualTo(newAssignee);
            assertThat(notification.getNotificationType()).isEqualTo(NotificationType.ISSUE_ASSIGNED);
            assertThat(notification.getResourceId()).isEqualTo(issueId);
        });
        assertThat(emailLogRepository.count()).isOne();
    }

    @Test
    void issueCreationNotifiesInitialAssigneesWithoutCopyingDescription() {
        UUID assigneeId = UUID.randomUUID();
        projectionService.consume(event(
                UUID.randomUUID(),
                "issue.created",
                new EventScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(),
                Map.of(
                        "assigneeIds", List.of(assigneeId),
                        "description", "sensitive issue body must not enter notifications or email outbox")));

        assertThat(notificationRepository.findAll()).singleElement()
                .extracting(Notification::getRecipientUserId)
                .isEqualTo(assigneeId);
        assertThat(emailOutboxRepository.count()).isOne();
    }

    @Test
    void malformedBoundEventRollsBackItsConsumedEventClaim() {
        UUID eventId = UUID.randomUUID();
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> malformed = event(
                eventId,
                "workspace.member.added",
                new EventScope(UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(),
                Map.of("role", "MEMBER"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> projectionService.consume(malformed))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(consumedEventRepository.existsById(eventId)).isFalse();
        assertThat(notificationRepository.count()).isZero();
        assertThat(emailOutboxRepository.count()).isZero();
    }

    @Test
    void concurrentDuplicateDeliveryHasOneAtomicWinner() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event = event(
                eventId,
                "comment.mentioned",
                new EventScope(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(),
                Map.of("recipientUserIds", List.of(UUID.randomUUID())));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return projectionService.consume(event);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return projectionService.consume(event);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(notificationRepository.countBySourceEventId(eventId)).isOne();
            assertThat(consumedEventRepository.count()).isOne();
            assertThat(emailOutboxRepository.count()).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    private EventEnvelope<com.fasterxml.jackson.databind.JsonNode> event(
            UUID eventId,
            String eventType,
            EventScope scope,
            UUID aggregateId,
            Map<String, ?> payload) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                1,
                Instant.now(),
                scope,
                aggregateId,
                objectMapper.valueToTree(payload),
                Map.of());
    }
}
