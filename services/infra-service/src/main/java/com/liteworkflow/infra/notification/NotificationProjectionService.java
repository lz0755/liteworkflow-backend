package com.liteworkflow.infra.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.liteworkflow.common.core.event.EventEnvelope;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationProjectionService {

    private final NotificationRepository notificationRepository;
    private final ConsumedNotificationEventRepository consumedEventRepository;
    private final Clock clock;

    public NotificationProjectionService(
            NotificationRepository notificationRepository,
            ConsumedNotificationEventRepository consumedEventRepository,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.consumedEventRepository = consumedEventRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean consume(EventEnvelope<JsonNode> envelope) {
        if (consumedEventRepository.existsById(envelope.eventId())) {
            return false;
        }

        NotificationTemplate template = template(envelope.eventType());
        Set<UUID> recipientIds = recipientIds(envelope.payload().path("recipientUserIds"));
        if (envelope.scope().actorId() != null) {
            recipientIds.remove(envelope.scope().actorId());
        }
        Instant now = clock.instant();
        List<Notification> notifications = recipientIds.stream()
                .map(recipientId -> new Notification(
                        UUID.randomUUID(),
                        recipientId,
                        template.type(),
                        template.resourceType(),
                        envelope.aggregateId(),
                        envelope.scope().actorId(),
                        envelope.scope().workspaceId(),
                        envelope.scope().projectId(),
                        template.title(),
                        template.message(),
                        envelope.eventId(),
                        now))
                .toList();
        notificationRepository.saveAll(notifications);
        consumedEventRepository.save(
                new ConsumedNotificationEvent(envelope.eventId(), envelope.eventType(), now));
        return true;
    }

    private NotificationTemplate template(String eventType) {
        return switch (eventType) {
            case "comment.mentioned" -> new NotificationTemplate(
                    NotificationType.COMMENT_MENTION,
                    "COMMENT",
                    "You were mentioned in a comment",
                    "A project member mentioned you in an issue comment.");
            case "issue.state.changed" -> new NotificationTemplate(
                    NotificationType.ISSUE_STATE_CHANGED,
                    "ISSUE",
                    "An issue changed state",
                    "A subscribed issue moved to a different state.");
            default -> throw new IllegalArgumentException("Unsupported notification event type: " + eventType);
        };
    }

    private Set<UUID> recipientIds(JsonNode node) {
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(value -> recipients.add(UUID.fromString(value.asText())));
        }
        return recipients;
    }

    private record NotificationTemplate(
            NotificationType type,
            String resourceType,
            String title,
            String message) {
    }
}
