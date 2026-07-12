package com.liteworkflow.core.dto.response;

import java.util.UUID;

public record NotificationContactResponse(UUID userId, String email) {

    @Override
    public String toString() {
        return "NotificationContactResponse[userId=" + userId + ", email=[REDACTED]]";
    }
}
