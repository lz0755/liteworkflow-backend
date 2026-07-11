package com.liteworkflow.core.directory;

import com.liteworkflow.core.domain.AccountStatus;
import java.util.UUID;

/** Consumer-owned copy of the stable identity user event contract. */
public record IdentityUserEventPayload(
        UUID userId,
        String email,
        String displayName,
        AccountStatus status,
        long version) {
}
