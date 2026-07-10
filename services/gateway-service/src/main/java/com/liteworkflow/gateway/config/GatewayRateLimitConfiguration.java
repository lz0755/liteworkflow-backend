package com.liteworkflow.gateway.config;

import com.liteworkflow.common.security.user.CurrentUser;
import com.liteworkflow.gateway.security.JwtAuthenticationWebFilter;
import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class GatewayRateLimitConfiguration {

    @Bean
    @Primary
    RedisRateLimiter loginRateLimiter(GatewayProperties properties) {
        return redisRateLimiter(properties.getRateLimits().getLogin());
    }

    @Bean
    RedisRateLimiter userSearchRateLimiter(GatewayProperties properties) {
        return redisRateLimiter(properties.getRateLimits().getUserSearch());
    }

    @Bean
    RedisRateLimiter aiRateLimiter(GatewayProperties properties) {
        return redisRateLimiter(properties.getRateLimits().getAi());
    }

    @Bean
    RedisRateLimiter sseRateLimiter(GatewayProperties properties) {
        return redisRateLimiter(properties.getRateLimits().getSse());
    }

    @Bean
    @Primary
    KeyResolver loginKeyResolver() {
        // Do not trust a client-controlled forwarding header unless a trusted proxy is configured.
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(address -> "login:" + (address.getAddress() == null
                        ? address.getHostString()
                        : address.getAddress().getHostAddress()))
                .defaultIfEmpty("login:unknown");
    }

    @Bean
    KeyResolver authenticatedUserKeyResolver() {
        return exchange -> {
            CurrentUser user = exchange.getAttribute(JwtAuthenticationWebFilter.CURRENT_USER_ATTRIBUTE);
            return Mono.justOrEmpty(user).map(currentUser -> "user:" + currentUser.userId());
        };
    }

    private static RedisRateLimiter redisRateLimiter(GatewayProperties.Limit limit) {
        return new RedisRateLimiter(
                limit.getReplenishRate(), limit.getBurstCapacity(), limit.getRequestedTokens());
    }
}
