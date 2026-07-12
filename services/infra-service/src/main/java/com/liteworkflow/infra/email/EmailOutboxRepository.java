package com.liteworkflow.infra.email;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxJob, UUID> {

    @Query("""
            select e.id from EmailOutboxJob e
             where e.status in :statuses
               and e.nextAttemptAt <= :now
             order by e.nextAttemptAt asc, e.createdAt asc, e.id asc
            """)
    List<UUID> findDueIds(
            Collection<EmailDeliveryStatus> statuses,
            Instant now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailOutboxJob e where e.id = :id")
    Optional<EmailOutboxJob> findByIdForUpdate(UUID id);
}
