package com.liteworkflow.gateway.trace;

import java.util.List;
import java.util.Locale;

/** Headers that are trusted only when written by the gateway after authentication. */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLES = "X-User-Roles";

    public static final List<String> INTERNAL_IDENTITY_HEADERS = List.of(
            USER_ID,
            USERNAME,
            USER_ROLES,
            "X-Roles",
            "X-User-Role",
            "X-Internal-User-Id",
            "X-Internal-Username");

    private GatewayHeaders() {
    }

    public static boolean isInternalIdentityHeader(String name) {
        String normalized = name.toUpperCase(Locale.ROOT);
        return normalized.startsWith("X-USER-")
                || normalized.equals("X-USERNAME")
                || normalized.equals("X-ROLES")
                || normalized.startsWith("X-INTERNAL-")
                || INTERNAL_IDENTITY_HEADERS.stream().anyMatch(header -> header.equalsIgnoreCase(name));
    }
}
