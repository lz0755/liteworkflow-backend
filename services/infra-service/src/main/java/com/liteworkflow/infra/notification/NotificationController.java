package com.liteworkflow.infra.notification;

import com.liteworkflow.common.core.api.ApiResponse;
import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.security.user.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationApplicationService service;

    public NotificationController(NotificationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/notifications")
    public ApiResponse<PageResult<NotificationResponse>> list(
            CurrentUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(user.userId(), page, size));
    }

    @GetMapping("/api/v1/notifications/unread-count")
    public ApiResponse<UnreadNotificationCountResponse> unreadCount(CurrentUser user) {
        return ApiResponse.success(service.unreadCount(user.userId()));
    }

    @PatchMapping("/api/v1/notifications/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
            CurrentUser user, @PathVariable UUID notificationId) {
        return ApiResponse.success(service.markRead(user.userId(), notificationId));
    }

    @PatchMapping("/api/v1/notifications/read-all")
    public ApiResponse<Void> markAllRead(CurrentUser user) {
        service.markAllRead(user.userId());
        return ApiResponse.success();
    }
}
