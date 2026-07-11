package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.IssueState;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueStateRepository extends JpaRepository<IssueState, UUID> {

    List<IssueState> findByProjectIdOrderByPositionAsc(UUID projectId);
}
