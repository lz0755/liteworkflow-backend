package com.liteworkflow.core.application;

import com.liteworkflow.core.domain.WorkspaceRole;
import java.util.Optional;
import java.util.UUID;

public interface WorkspacePermissionCache {

    Optional<WorkspaceRole> get(UUID workspaceId, UUID userId);

    void put(UUID workspaceId, UUID userId, WorkspaceRole role);

    void evict(UUID workspaceId, UUID userId);
}
