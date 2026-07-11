package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueLabel;
import com.liteworkflow.core.domain.IssueLabelStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, UUID> {

    List<IssueLabel> findByProjectIdAndStatusOrderByNameAscIdAsc(UUID projectId, IssueLabelStatus status);

    Optional<IssueLabel> findByIdAndProjectIdAndStatus(UUID id, UUID projectId, IssueLabelStatus status);

    @Query("""
            select l from IssueLabel l
             where l.projectId = :projectId
               and l.status = com.liteworkflow.core.domain.IssueLabelStatus.ACTIVE
               and l.id in :ids
            """)
    List<IssueLabel> findActiveByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    boolean existsByProjectIdAndStatusAndNameIgnoreCase(
            UUID projectId, IssueLabelStatus status, String name);

    boolean existsByProjectIdAndStatusAndNameIgnoreCaseAndIdNot(
            UUID projectId, IssueLabelStatus status, String name, UUID id);
}
