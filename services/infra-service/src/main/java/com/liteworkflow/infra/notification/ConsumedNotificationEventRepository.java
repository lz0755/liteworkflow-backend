package com.liteworkflow.infra.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedNotificationEventRepository
        extends JpaRepository<ConsumedNotificationEvent, UUID> {
}
