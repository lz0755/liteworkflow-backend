package com.liteworkflow.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liteworkflow.common.security.user.CurrentUser;
import io.jsonwebtoken.io.Encoders;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    @Test
    void issuesAndVerifiesAccessToken() {
        JwtTokenService tokens = new JwtTokenService(properties());
        CurrentUser user = new CurrentUser(UUID.randomUUID(), "ada", Set.of("OWNER", "MEMBER"));

        CurrentUser parsed = tokens.parseAccessToken(tokens.issueAccessToken(user));

        assertThat(parsed).isEqualTo(user);
    }

    @Test
    void rejectsMalformedTokenAndMissingBearerPrefix() {
        JwtTokenService tokens = new JwtTokenService(properties());

        assertThatThrownBy(() -> tokens.parseAccessToken("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> JwtTokenService.removeBearerPrefix("Basic value"))
                .isInstanceOf(InvalidTokenException.class);
    }

    private JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        byte[] key = "test-only-32-byte-signing-key-000".getBytes(StandardCharsets.UTF_8);
        properties.setSecret(Encoders.BASE64.encode(key));
        return properties;
    }
}
