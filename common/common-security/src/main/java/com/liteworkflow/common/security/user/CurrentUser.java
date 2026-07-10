package com.liteworkflow.common.security.user;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CurrentUser(UUID userId, String username, Set<String> roles) {

    public CurrentUser {
        Objects.requireNonNull(userId, "userId must not be null");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
