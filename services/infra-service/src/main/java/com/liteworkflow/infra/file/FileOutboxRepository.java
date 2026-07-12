package com.liteworkflow.infra.file;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileOutboxRepository extends JpaRepository<FileOutboxEvent, UUID> {
    List<FileOutboxEvent> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            Collection<FileOutboxEvent.Status> statuses, Instant now, Pageable pageable);
}
