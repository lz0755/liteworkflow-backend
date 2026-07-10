package com.liteworkflow.identity.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** Local, after-commit notification only. It is never written to the integration outbox. */
public record PasswordResetMailRequested(UUID userId, String email, String rawToken, Instant expiresAt) {

    @Override
    public String toString() {
        return "PasswordResetMailRequested[userId=" + userId + ", email=[REDACTED], rawToken=[REDACTED], expiresAt="
                + expiresAt + "]";
    }
}
