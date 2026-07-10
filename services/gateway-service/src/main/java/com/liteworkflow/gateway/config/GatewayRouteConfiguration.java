package com.liteworkflow.gateway.config;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayRouteConfiguration {

    @Bean
    RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            GatewayProperties properties,
            @Qualifier("loginRateLimiter") RedisRateLimiter loginRateLimiter,
            @Qualifier("userSearchRateLimiter") RedisRateLimiter userSearchRateLimiter,
            @Qualifier("aiRateLimiter") RedisRateLimiter aiRateLimiter,
            @Qualifier("sseRateLimiter") RedisRateLimiter sseRateLimiter,
            @Qualifier("loginKeyResolver") KeyResolver loginKeyResolver,
            @Qualifier("authenticatedUserKeyResolver") KeyResolver authenticatedUserKeyResolver) {
        RouteLocatorBuilder.Builder routes = builder.routes();

        routes.route("identity-login", route -> route
                .path("/api/v1/auth/login").and().method(HttpMethod.POST)
                .filters(filters -> filters.requestRateLimiter(config -> config
                        .setRateLimiter(loginRateLimiter)
                        .setKeyResolver(loginKeyResolver)))
                .uri(properties.getServices().getIdentity()));
        routes.route("identity-api", route -> route
                .path("/api/v1/auth/**")
                .uri(properties.getServices().getIdentity()));

        routes.route("core-user-search", route -> route
                .path("/api/v1/users/search").and().method(HttpMethod.GET)
                .filters(filters -> filters.requestRateLimiter(config -> config
                        .setRateLimiter(userSearchRateLimiter)
                        .setKeyResolver(authenticatedUserKeyResolver)))
                .uri(properties.getServices().getCore()));

        // Issue attachments belong to infra-service and must precede the broad issue route.
        routes.route("infra-issue-attachments", route -> route
                .path("/api/v1/issues/{issueId}/attachments")
                .uri(properties.getServices().getInfra()));
        routes.route("core-api", route -> route
                .path(
                        "/api/v1/users/**",
                        "/api/v1/workspaces/**",
                        "/api/v1/projects/**",
                        "/api/v1/issues/**",
                        "/api/v1/comments/**")
                .uri(properties.getServices().getCore()));
        routes.route("infra-api", route -> route
                .path(
                        "/api/v1/files/**",
                        "/api/v1/notifications/**",
                        "/api/v1/exports/**")
                .uri(properties.getServices().getInfra()));

        routes.route("ai-sse", route -> route
                .path("/api/v1/ai/assist/stream").and().method(HttpMethod.POST)
                .filters(filters -> filters
                        .requestRateLimiter(config -> config
                                .setRateLimiter(sseRateLimiter)
                                .setKeyResolver(authenticatedUserKeyResolver))
                        .setResponseHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-transform")
                        .setResponseHeader("X-Accel-Buffering", "no"))
                .metadata(RESPONSE_TIMEOUT_ATTR, -1L)
                .uri(properties.getServices().getAi()));
        routes.route("ai-api", route -> route
                .path("/api/v1/ai/**")
                .filters(filters -> filters.requestRateLimiter(config -> config
                        .setRateLimiter(aiRateLimiter)
                        .setKeyResolver(authenticatedUserKeyResolver)))
                .uri(properties.getServices().getAi()));

        if (properties.getOpenapi().isEnabled()) {
            routes.route("openapi-identity", route -> route
                    .path("/v3/api-docs/identity")
                    .uri(properties.getServices().getIdentity()));
            routes.route("openapi-core", route -> route
                    .path("/v3/api-docs/core")
                    .uri(properties.getServices().getCore()));
            routes.route("openapi-infra", route -> route
                    .path("/v3/api-docs/infra")
                    .uri(properties.getServices().getInfra()));
            routes.route("openapi-ai", route -> route
                    .path("/v3/api-docs/ai")
                    .uri(properties.getServices().getAi()));
        }
        return routes.build();
    }
}
