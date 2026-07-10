package com.liteworkflow.common.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("liteworkflow.security.jwt")
public class JwtProperties {

    private String secret;
    private String issuer = "liteworkflow";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration clockSkew = Duration.ofSeconds(30);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("liteworkflow.security.jwt.secret must not be blank");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("liteworkflow.security.jwt.issuer must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        if (clockSkew == null || clockSkew.isNegative()) {
            throw new IllegalArgumentException("JWT clock skew must not be negative");
        }
    }
}
