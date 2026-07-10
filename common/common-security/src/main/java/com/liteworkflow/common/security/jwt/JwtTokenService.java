package com.liteworkflow.common.security.jwt;

import com.liteworkflow.common.security.user.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;

public final class JwtTokenService {

    private static final String USERNAME_CLAIM = "username";
    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Clock clock;

    public JwtTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenService(JwtProperties properties, Clock clock) {
        properties.validate();
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
        this.clock = clock;
    }

    public String issueAccessToken(CurrentUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        return Jwts.builder()
                .subject(user.userId().toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(USERNAME_CLAIM, user.username())
                .claim(ROLES_CLAIM, user.roles().stream().sorted().toList())
                .signWith(signingKey)
                .compact();
    }

    public CurrentUser parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .clockSkewSeconds(properties.getClockSkew().toSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            String username = claims.get(USERNAME_CLAIM, String.class);
            List<?> roleValues = claims.get(ROLES_CLAIM, List.class);
            Set<String> roles = new HashSet<>();
            if (roleValues != null) {
                roleValues.forEach(role -> roles.add(String.valueOf(role)));
            }
            return new CurrentUser(userId, username, roles);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException(exception);
        }
    }

    public static String removeBearerPrefix(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new InvalidTokenException();
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new InvalidTokenException();
        }
        return token;
    }
}
