package com.liteworkflow.core.application;

import java.util.UUID;

public record ProjectPermissionInvalidation(UUID projectId, UUID userId) {
}
