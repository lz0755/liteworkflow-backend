package com.liteworkflow.core.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PermissionCacheInvalidationListener {

    private final WorkspacePermissionCache permissionCache;

    public PermissionCacheInvalidationListener(WorkspacePermissionCache permissionCache) {
        this.permissionCache = permissionCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evict(WorkspacePermissionInvalidation invalidation) {
        permissionCache.evict(invalidation.workspaceId(), invalidation.userId());
    }
}
