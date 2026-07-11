package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.LocalOutboxEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LocalOutboxEventRepository extends JpaRepository<LocalOutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from LocalOutboxEvent e where e.id = :id")
    Optional<LocalOutboxEvent> findByIdForUpdate(UUID id);

    @Query("""
            select e.id from LocalOutboxEvent e
             where e.status = com.liteworkflow.core.domain.OutboxStatus.PENDING
                or (e.status = com.liteworkflow.core.domain.OutboxStatus.FAILED
                    and e.nextRetryAt <= :now)
             order by e.createdAt asc
            """)
    List<UUID> findRecoverableIds(Instant now, Pageable pageable);
}
