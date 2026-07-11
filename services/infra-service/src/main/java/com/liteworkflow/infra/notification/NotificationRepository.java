package com.liteworkflow.infra.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    long countBySourceEventId(UUID sourceEventId);
}
