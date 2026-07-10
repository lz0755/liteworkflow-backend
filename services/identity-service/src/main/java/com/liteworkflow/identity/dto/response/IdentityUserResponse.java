package com.liteworkflow.identity.dto.response;

import com.liteworkflow.identity.application.IdentityUserView;
import com.liteworkflow.identity.domain.IdentityStatus;
import java.util.UUID;

public record IdentityUserResponse(UUID userId, String email, String displayName, IdentityStatus status, long version) {

    public static IdentityUserResponse from(IdentityUserView view) {
        return new IdentityUserResponse(
                view.userId(), view.email(), view.displayName(), view.status(), view.version());
    }

    @Override
    public String toString() {
        return "IdentityUserResponse[userId=" + userId + ", email=[REDACTED], displayName=" + displayName
                + ", status=" + status + ", version=" + version + "]";
    }
}
