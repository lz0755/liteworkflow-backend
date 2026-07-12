package com.liteworkflow.infra.email;

import java.util.UUID;

public record NotificationContact(UUID userId, String email) {

    @Override
    public String toString() {
        return "NotificationContact[userId=" + userId + ", email=[REDACTED]]";
    }
}
