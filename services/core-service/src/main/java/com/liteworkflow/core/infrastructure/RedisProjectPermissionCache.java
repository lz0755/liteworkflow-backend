package com.liteworkflow.core.infrastructure;

import com.liteworkflow.core.application.ProjectPermissionCache;
import com.liteworkflow.core.domain.ProjectRole;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "liteworkflow.core",
        name = "permission-cache-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RedisProjectPermissionCache implements ProjectPermissionCache {

    private static final Logger log = LoggerFactory.getLogger(RedisProjectPermissionCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public RedisProjectPermissionCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<ProjectRole> get(UUID projectId, UUID userId) {
        try {
            String value = redisTemplate.opsForValue().get(permissionKey(projectId, userId));
            return value == null ? Optional.empty() : Optional.of(ProjectRole.valueOf(value));
        } catch (RuntimeException exception) {
            log.warn("Project permission cache read failed; using database fallback");
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID projectId, UUID userId, ProjectRole role) {
        try {
            redisTemplate.opsForValue().set(permissionKey(projectId, userId), role.name(), TTL);
        } catch (RuntimeException exception) {
            log.warn("Project permission cache write failed; continuing without cache");
        }
    }

    @Override
    public void evict(UUID projectId, UUID userId) {
        try {
            redisTemplate.delete(permissionKey(projectId, userId));
            redisTemplate.delete(memberListKey(projectId));
        } catch (RuntimeException exception) {
            log.warn("Project permission cache eviction failed; database checks remain authoritative");
        }
    }

    private String permissionKey(UUID projectId, UUID userId) {
        return "perm:project:" + projectId + ":user:" + userId;
    }

    private String memberListKey(UUID projectId) {
        return "project:members:" + projectId;
    }
}
