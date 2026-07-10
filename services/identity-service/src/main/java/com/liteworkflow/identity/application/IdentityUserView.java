package com.liteworkflow.identity.application;

import com.liteworkflow.identity.domain.IdentityStatus;
import java.util.UUID;

public record IdentityUserView(UUID userId, String email, String displayName, IdentityStatus status, long version) {
}
