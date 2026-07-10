package com.liteworkflow.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "liteworkflow.gateway.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayOpenApiConfiguration {

    private static final String BEARER_JWT = "bearer-jwt";

    @Bean
    OpenAPI gatewayOpenApi() {
        return new OpenAPI()
                .info(new Info().title("liteworkflow gateway API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    GroupedOpenApi gatewayOpenApiGroup() {
        return GroupedOpenApi.builder()
                .group("gateway")
                .pathsToMatch("/actuator/health")
                .build();
    }
}
