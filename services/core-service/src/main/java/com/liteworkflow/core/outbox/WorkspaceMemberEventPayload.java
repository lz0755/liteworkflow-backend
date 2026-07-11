package com.liteworkflow.core.outbox;

import com.liteworkflow.core.domain.WorkspaceRole;
import java.util.UUID;

public record WorkspaceMemberEventPayload(
        UUID memberUserId, WorkspaceRole role, WorkspaceRole previousRole) {
}
