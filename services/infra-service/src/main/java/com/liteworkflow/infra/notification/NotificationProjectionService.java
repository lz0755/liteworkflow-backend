package com.liteworkflow.infra.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.liteworkflow.common.core.event.EventEnvelope;
import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.infra.email.EmailLog;
import com.liteworkflow.infra.email.EmailLogRepository;
import com.liteworkflow.infra.email.EmailOutboxJob;
import com.liteworkflow.infra.email.EmailOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationProjectionService {

    private final NotificationRepository notificationRepository;
    private final ConsumedEventClaimRepository consumedEventClaimRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final Clock clock;

    public NotificationProjectionService(
            NotificationRepository notificationRepository,
            ConsumedEventClaimRepository consumedEventClaimRepository,
            EmailLogRepository emailLogRepository,
            EmailOutboxRepository emailOutboxRepository,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.consumedEventClaimRepository = consumedEventClaimRepository;
        this.emailLogRepository = emailLogRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean consume(EventEnvelope<JsonNode> envelope) {
        Instant now = clock.instant();
        if (!consumedEventClaimRepository.claim(envelope.eventId(), envelope.eventType(), now)) {
            return false;
        }

        NotificationTemplate template = template(envelope.eventType());
        Set<UUID> recipientIds = recipientIds(envelope);
        if (envelope.scope().actorId() != null) {
            recipientIds.remove(envelope.scope().actorId());
        }
        UUID resourceId = resourceId(envelope, template.resourceType());
        String traceId = TraceIds.resolve(TraceIds.current());
        List<Notification> notifications = recipientIds.stream()
                .map(recipientId -> new Notification(
                        UUID.randomUUID(),
                        recipientId,
                        template.type(),
                        template.resourceType(),
                        resourceId,
                        envelope.scope().actorId(),
                        envelope.scope().workspaceId(),
                        envelope.scope().projectId(),
                        template.title(),
                        template.message(),
                        envelope.eventId(),
                        now))
                .toList();
        notificationRepository.saveAll(notifications);

        List<EmailLog> emailLogs = new ArrayList<>(recipientIds.size());
        List<EmailOutboxJob> emailJobs = new ArrayList<>(recipientIds.size());
        for (UUID recipientId : recipientIds) {
            UUID emailLogId = UUID.randomUUID();
            String templateCode = template.type().name();
            emailLogs.add(new EmailLog(
                    emailLogId,
                    envelope.eventId(),
                    recipientId,
                    templateCode,
                    now));
            emailJobs.add(new EmailOutboxJob(
                    UUID.randomUUID(),
                    emailLogId,
                    envelope.eventId(),
                    envelope.eventType(),
                    traceId,
                    recipientId,
                    templateCode,
                    template.resourceType(),
                    resourceId,
                    envelope.scope().workspaceId(),
                    envelope.scope().projectId(),
                    now));
        }
        emailLogRepository.saveAll(emailLogs);
        emailOutboxRepository.saveAll(emailJobs);
        return true;
    }

    private NotificationTemplate template(String eventType) {
        return switch (eventType) {
            case "workspace.member.added" -> new NotificationTemplate(
                    NotificationType.WORKSPACE_MEMBER_ADDED,
                    "WORKSPACE",
                    "You were added to a workspace",
                    "You now have access to a workspace.");
            case "project.member.added" -> new NotificationTemplate(
                    NotificationType.PROJECT_MEMBER_ADDED,
                    "PROJECT",
                    "You were added to a project",
                    "You now have access to a project.");
            case "issue.created", "issue.assignees.changed" -> new NotificationTemplate(
                    NotificationType.ISSUE_ASSIGNED,
                    "ISSUE",
                    "An issue was assigned to you",
                    "A project issue was assigned to you.");
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

    private Set<UUID> recipientIds(EventEnvelope<JsonNode> envelope) {
        JsonNode payload = envelope.payload();
        return switch (envelope.eventType()) {
            case "workspace.member.added", "project.member.added" -> {
                LinkedHashSet<UUID> recipient = new LinkedHashSet<>();
                recipient.add(requiredUuid(payload.path("memberUserId"), "memberUserId"));
                yield recipient;
            }
            case "issue.created" -> uuidSet(payload.path("assigneeIds"), "assigneeIds");
            case "issue.assignees.changed" -> {
                LinkedHashSet<UUID> added = uuidSet(payload.path("assigneeIds"), "assigneeIds");
                added.removeAll(uuidSet(payload.path("previousAssigneeIds"), "previousAssigneeIds"));
                yield added;
            }
            case "comment.mentioned", "issue.state.changed" ->
                    uuidSet(payload.path("recipientUserIds"), "recipientUserIds");
            default -> throw new IllegalArgumentException("Unsupported notification event type");
        };
    }

    private LinkedHashSet<UUID> uuidSet(JsonNode node, String fieldName) {
        if (!node.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        for (JsonNode value : node) {
            recipients.add(requiredUuid(value, fieldName));
        }
        return recipients;
    }

    private UUID requiredUuid(JsonNode node, String fieldName) {
        if (!node.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must contain UUID strings");
        }
        try {
            return UUID.fromString(node.textValue());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " contains an invalid UUID");
        }
    }

    private UUID resourceId(EventEnvelope<JsonNode> envelope, String resourceType) {
        UUID resourceId = switch (resourceType) {
            case "WORKSPACE" -> envelope.scope().workspaceId();
            case "PROJECT" -> envelope.scope().projectId();
            default -> envelope.aggregateId();
        };
        if (resourceId == null) {
            throw new IllegalArgumentException("Notification event is missing its resource scope");
        }
        return resourceId;
    }

    private record NotificationTemplate(
            NotificationType type,
            String resourceType,
            String title,
            String message) {
    }
}
