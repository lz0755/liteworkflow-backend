package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.ProjectIssueCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProjectIssueCounterRepository extends JpaRepository<ProjectIssueCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProjectIssueCounter c where c.projectId = :projectId")
    Optional<ProjectIssueCounter> findByProjectIdForUpdate(UUID projectId);
}
