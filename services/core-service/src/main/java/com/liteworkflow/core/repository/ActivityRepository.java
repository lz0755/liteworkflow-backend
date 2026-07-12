package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.Activity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    long countByProjectId(UUID projectId);

    List<Activity> findTop200ByProjectIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            UUID projectId, Instant fromInclusive, Instant toExclusive);
}
