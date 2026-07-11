package com.liteworkflow.core.config;

import com.liteworkflow.core.application.ProjectPermissionCache;
import com.liteworkflow.core.application.WorkspacePermissionCache;
import com.liteworkflow.core.domain.ProjectRole;
import com.liteworkflow.core.domain.WorkspaceRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PermissionCacheConfiguration {

    @Bean
    @ConditionalOnMissingBean(WorkspacePermissionCache.class)
    WorkspacePermissionCache noOpWorkspacePermissionCache() {
        return new WorkspacePermissionCache() {
            @Override
            public Optional<WorkspaceRole> get(UUID workspaceId, UUID userId) {
                return Optional.empty();
            }

            @Override
            public void put(UUID workspaceId, UUID userId, WorkspaceRole role) {
            }

            @Override
            public void evict(UUID workspaceId, UUID userId) {
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ProjectPermissionCache.class)
    ProjectPermissionCache noOpProjectPermissionCache() {
        return new ProjectPermissionCache() {
            @Override
            public Optional<ProjectRole> get(UUID projectId, UUID userId) {
                return Optional.empty();
            }

            @Override
            public void put(UUID projectId, UUID userId, ProjectRole role) {
            }

            @Override
            public void evict(UUID projectId, UUID userId) {
            }
        };
    }
}
