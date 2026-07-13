package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueComment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

    Page<IssueComment> findByIssueIdAndDeletedAtIsNull(UUID issueId, Pageable pageable);
    List<IssueComment> findByIssueId(UUID issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IssueComment c where c.id = :commentId and c.deletedAt is null")
    Optional<IssueComment> findActiveByIdForUpdate(UUID commentId);

    @Query("select c.issueId from IssueComment c where c.id = :commentId and c.deletedAt is null")
    Optional<UUID> findActiveIssueId(UUID commentId);
}
