package com.liteworkflow.infra.notification;

import com.liteworkflow.common.core.api.PageResult;
import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationApplicationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationApplicationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationResponse> list(UUID userId, int page, int size) {
        validatePage(page, size);
        var result = repository.findByRecipientUserId(
                userId,
                PageRequest.of(
                        page - 1,
                        size,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResult.of(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getTotalElements(),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount(UUID userId) {
        return new UnreadNotificationCountResponse(
                repository.countByRecipientUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = repository.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(() -> new BizException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, clock.instant());
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BizException(CommonErrorCode.VALIDATION_ERROR, "Invalid pagination");
        }
    }
}
