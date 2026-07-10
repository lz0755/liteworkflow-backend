package com.liteworkflow.gateway.config;

import com.liteworkflow.common.core.error.CommonErrorCode;
import com.liteworkflow.common.security.jwt.JwtTokenService;
import com.liteworkflow.gateway.security.JwtAuthenticationWebFilter;
import com.liteworkflow.gateway.security.SecurityResponseWriter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration(proxyBeanMethods = false)
public class GatewaySecurityConfiguration {

    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    };

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            JwtTokenService jwtTokenService,
            SecurityResponseWriter responseWriter,
            GatewayProperties properties) {
        List<String> publicGetEndpoints = new ArrayList<>();
        publicGetEndpoints.add("/actuator/health");
        if (properties.getOpenapi().isEnabled()) {
            publicGetEndpoints.addAll(List.of(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/webjars/**",
                    "/v3/api-docs/**"));
        }

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, exception) -> responseWriter.write(
                                exchange, CommonErrorCode.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED.defaultMessage()))
                        .accessDeniedHandler((exchange, exception) -> responseWriter.write(
                                exchange, CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.defaultMessage())))
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .pathMatchers(HttpMethod.GET, publicGetEndpoints.toArray(String[]::new)).permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(
                        new JwtAuthenticationWebFilter(jwtTokenService, responseWriter),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
