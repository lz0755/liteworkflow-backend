package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.Issue;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

    @Query("select i.projectId from Issue i where i.id = :issueId and i.deletedAt is null")
    Optional<UUID> findActiveProjectId(UUID issueId);

    @Query("select i from Issue i where i.id = :issueId and i.deletedAt is null")
    Optional<Issue> findActiveById(UUID issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issue i where i.id = :issueId and i.deletedAt is null")
    Optional<Issue> findActiveByIdForUpdate(UUID issueId);

    Optional<Issue> findByProjectIdAndClientRequestId(UUID projectId, UUID clientRequestId);

    boolean existsByStateIdAndDeletedAtIsNull(UUID stateId);
}
