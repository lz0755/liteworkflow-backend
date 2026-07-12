package com.liteworkflow.infra.file;

import java.util.UUID;

public interface FileAccessAuthorizer {
    AccessContext authorize(UUID userId, FileScope scope, UUID resourceId, AccessAction action);
    enum AccessAction { READ, WRITE }
}
