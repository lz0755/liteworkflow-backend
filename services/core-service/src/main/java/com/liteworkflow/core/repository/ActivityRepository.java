package com.liteworkflow.core.repository;

import com.liteworkflow.core.domain.Activity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    long countByProjectId(UUID projectId);
}
