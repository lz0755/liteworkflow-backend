package com.liteworkflow.core.infrastructure;

import com.liteworkflow.core.application.WorkspacePermissionCache;
import com.liteworkflow.core.domain.WorkspaceRole;
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
public class RedisWorkspacePermissionCache implements WorkspacePermissionCache {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkspacePermissionCache.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public RedisWorkspacePermissionCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<WorkspaceRole> get(UUID workspaceId, UUID userId) {
        try {
            String value = redisTemplate.opsForValue().get(permissionKey(workspaceId, userId));
            return value == null ? Optional.empty() : Optional.of(WorkspaceRole.valueOf(value));
        } catch (RuntimeException exception) {
            log.warn("Workspace permission cache read failed; using database fallback");
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID workspaceId, UUID userId, WorkspaceRole role) {
        try {
            redisTemplate.opsForValue().set(permissionKey(workspaceId, userId), role.name(), TTL);
        } catch (RuntimeException exception) {
            log.warn("Workspace permission cache write failed; continuing without cache");
        }
    }

    @Override
    public void evict(UUID workspaceId, UUID userId) {
        try {
            redisTemplate.delete(permissionKey(workspaceId, userId));
            redisTemplate.delete(memberListKey(workspaceId));
        } catch (RuntimeException exception) {
            log.warn("Workspace permission cache eviction failed; database checks remain authoritative");
        }
    }

    private String permissionKey(UUID workspaceId, UUID userId) {
        return "perm:workspace:" + workspaceId + ":user:" + userId;
    }

    private String memberListKey(UUID workspaceId) {
        return "workspace:members:" + workspaceId;
    }
}
