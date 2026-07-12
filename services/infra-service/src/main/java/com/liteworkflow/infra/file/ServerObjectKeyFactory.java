package com.liteworkflow.infra.file;

import com.liteworkflow.common.file.storage.ObjectKeys;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ServerObjectKeyFactory {
    private final Clock clock;
    public ServerObjectKeyFactory(Clock clock) { this.clock = clock; }

    public String create(FilePurpose purpose, UUID workspaceId, UUID projectId, UUID scopeId,
            UUID fileId, String extension) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        StringBuilder key = new StringBuilder(purpose.keyPrefix()).append('/');
        if (workspaceId != null) key.append(workspaceId).append('/');
        if (projectId != null) key.append(projectId).append('/');
        if (purpose.scope() == FileScope.USER || purpose.scope() == FileScope.ISSUE) key.append(scopeId).append('/');
        key.append(now.getYear()).append('/').append(String.format("%02d", now.getMonthValue()))
                .append('/').append(fileId).append('.').append(extension);
        return ObjectKeys.requireSafe(key.toString());
    }
}
