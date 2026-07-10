package com.liteworkflow.identity.dto.response;

import com.liteworkflow.identity.application.TokenPair;
import java.time.Instant;

public record AuthTokensResponse(String accessToken, String refreshToken, Instant refreshTokenExpiresAt) {

    public static AuthTokensResponse from(TokenPair pair) {
        return new AuthTokensResponse(pair.accessToken(), pair.refreshToken(), pair.refreshTokenExpiresAt());
    }

    @Override
    public String toString() {
        return "AuthTokensResponse[accessToken=[REDACTED], refreshToken=[REDACTED], refreshTokenExpiresAt="
                + refreshTokenExpiresAt + "]";
    }
}
