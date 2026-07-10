package com.liteworkflow.identity.application;

import java.time.Instant;

public record TokenPair(String accessToken, String refreshToken, Instant refreshTokenExpiresAt) {

    @Override
    public String toString() {
        return "TokenPair[accessToken=[REDACTED], refreshToken=[REDACTED], refreshTokenExpiresAt="
                + refreshTokenExpiresAt + "]";
    }
}
