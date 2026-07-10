package com.liteworkflow.common.core.event;

import java.util.UUID;

/** Optional tenant/resource scope carried by every integration event. */
public record EventScope(UUID workspaceId, UUID projectId, UUID actorId) {

    public static EventScope global() {
        return new EventScope(null, null, null);
    }
}
