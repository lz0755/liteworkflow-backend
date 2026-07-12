package com.liteworkflow.infra.file;

import java.util.UUID;

public record AccessContext(UUID workspaceId, UUID projectId, UUID issueId) {
    static AccessContext user(UUID userId) { return new AccessContext(null, null, null); }
}
