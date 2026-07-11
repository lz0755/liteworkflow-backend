package com.liteworkflow.core.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PermissionCacheInvalidationListener {

    private final WorkspacePermissionCache permissionCache;
    private final ProjectPermissionCache projectPermissionCache;

    public PermissionCacheInvalidationListener(
            WorkspacePermissionCache permissionCache,
            ProjectPermissionCache projectPermissionCache) {
        this.permissionCache = permissionCache;
        this.projectPermissionCache = projectPermissionCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evict(WorkspacePermissionInvalidation invalidation) {
        permissionCache.evict(invalidation.workspaceId(), invalidation.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evict(ProjectPermissionInvalidation invalidation) {
        projectPermissionCache.evict(invalidation.projectId(), invalidation.userId());
    }
}
