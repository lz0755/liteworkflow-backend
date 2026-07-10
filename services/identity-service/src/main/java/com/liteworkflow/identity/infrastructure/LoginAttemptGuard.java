package com.liteworkflow.identity.infrastructure;

import com.liteworkflow.identity.config.IdentityProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-backed, deliberately identifier-opaque login failure limiter. */
@Component
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

    private final StringRedisTemplate redis;
    private final IdentityProperties properties;
    private final SecretTokenService tokens;

    public LoginAttemptGuard(StringRedisTemplate redis, IdentityProperties properties, SecretTokenService tokens) {
        this.redis = redis;
        this.properties = properties;
        this.tokens = tokens;
    }

    public boolean isBlocked(String normalizedEmail, String clientIp) {
        try {
            String value = redis.opsForValue().get(key(normalizedEmail, clientIp));
            return value != null && Long.parseLong(value) >= properties.getLoginLimit().getMaxFailures();
        } catch (DataAccessException | NumberFormatException exception) {
            // Availability of Redis must not disclose whether a user exists or break all login traffic.
            log.warn("Login limiter unavailable; applying no identifier-specific decision");
            return false;
        }
    }

    public void recordFailure(String normalizedEmail, String clientIp) {
        try {
            String key = key(normalizedEmail, clientIp);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, properties.getLoginLimit().getWindow());
            }
        } catch (DataAccessException exception) {
            log.warn("Login limiter write failed");
        }
    }

    public void clear(String normalizedEmail, String clientIp) {
        try {
            redis.delete(key(normalizedEmail, clientIp));
        } catch (DataAccessException exception) {
            log.warn("Login limiter cleanup failed");
        }
    }

    private String key(String normalizedEmail, String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        return "identity:login:fail:" + tokens.hash(normalizedEmail) + ":" + tokens.hash(ip);
    }
}
