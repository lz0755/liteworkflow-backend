package com.liteworkflow.core.application;

import java.util.UUID;

public record WorkspacePermissionInvalidation(UUID workspaceId, UUID userId) {
}
