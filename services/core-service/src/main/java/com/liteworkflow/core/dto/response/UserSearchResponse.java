package com.liteworkflow.core.dto.response;

import java.util.UUID;

/** Deliberately restricted directory view for authorized member managers. */
public record UserSearchResponse(
        UUID userId,
        String displayName,
        String email,
        UUID avatarFileId,
        boolean workspaceMember,
        boolean eligible,
        String ineligibleReason) {

    /** Prevent framework debug logging from rendering searched email/display-name values. */
    @Override
    public String toString() {
        return "UserSearchResponse[userId=" + userId
                + ", workspaceMember=" + workspaceMember
                + ", eligible=" + eligible + "]";
    }
}
