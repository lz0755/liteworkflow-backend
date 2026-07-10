package com.liteworkflow.identity.outbox;

import com.liteworkflow.identity.domain.IdentityStatus;
import java.util.UUID;

/** Stable, non-sensitive user-directory projection sent to core-service. */
public record IdentityUserEventPayload(
        UUID userId, String email, String displayName, IdentityStatus status, long version) {
}
