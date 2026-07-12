package com.liteworkflow.infra.notification;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    long countBySourceEventId(UUID sourceEventId);

    Page<Notification> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

    java.util.Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.readAt = :readAt
             where n.recipientUserId = :recipientUserId
               and n.readAt is null
            """)
    int markAllRead(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("readAt") java.time.Instant readAt);
}
