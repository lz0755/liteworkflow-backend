package com.liteworkflow.infra.export;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    Optional<ExportJob> findByIdAndRequestedBy(UUID id, UUID requestedBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ExportJob j where j.id = :id")
    Optional<ExportJob> findByIdForUpdate(UUID id);
}
