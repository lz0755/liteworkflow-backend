package com.liteworkflow.core.outbox;

import com.liteworkflow.core.domain.ProjectRole;
import java.util.UUID;

public record ProjectMemberEventPayload(
        UUID memberUserId, ProjectRole role, ProjectRole previousRole) {
}
