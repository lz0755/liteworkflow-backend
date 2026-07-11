package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueState;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueStateRepository extends JpaRepository<IssueState, UUID> {

    List<IssueState> findByProjectIdOrderByPositionAsc(UUID projectId);

    List<IssueState> findByProjectIdAndStatusOrderByPositionAsc(
            UUID projectId, com.liteworkflow.core.domain.IssueStateStatus status);

    Optional<IssueState> findByIdAndProjectIdAndStatus(
            UUID id, UUID projectId, com.liteworkflow.core.domain.IssueStateStatus status);

    List<IssueState> findByIdIn(Collection<UUID> ids);

    Optional<IssueState> findByProjectIdAndDefaultStateTrueAndStatus(
            UUID projectId, com.liteworkflow.core.domain.IssueStateStatus status);

    boolean existsByProjectIdAndStatusAndNameIgnoreCase(
            UUID projectId, com.liteworkflow.core.domain.IssueStateStatus status, String name);

    boolean existsByProjectIdAndStatusAndNameIgnoreCaseAndIdNot(
            UUID projectId, com.liteworkflow.core.domain.IssueStateStatus status, String name, UUID id);

    boolean existsByProjectIdAndStatusAndPosition(
            UUID projectId, com.liteworkflow.core.domain.IssueStateStatus status, int position);

    boolean existsByProjectIdAndStatusAndPositionAndIdNot(
            UUID projectId, com.liteworkflow.core.domain.IssueStateStatus status, int position, UUID id);
}
