package com.liteworkflow.infra.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String resourceType,
        UUID resourceId,
        UUID actorId,
        UUID workspaceId,
        UUID projectId,
        String title,
        String message,
        Instant readAt,
        Instant createdAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getResourceType(),
                notification.getResourceId(),
                notification.getActorId(),
                notification.getWorkspaceId(),
                notification.getProjectId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
