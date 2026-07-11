package com.liteworkflow.core.application;

import com.liteworkflow.core.domain.ProjectRole;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPermissionCache {

    Optional<ProjectRole> get(UUID projectId, UUID userId);

    void put(UUID projectId, UUID userId, ProjectRole role);

    void evict(UUID projectId, UUID userId);
}
