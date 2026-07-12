package com.liteworkflow.infra.export;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ExportOutboxRepository extends JpaRepository<ExportOutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ExportOutboxEvent> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            Collection<ExportOutboxStatus> statuses, Instant now, Pageable pageable);
}
