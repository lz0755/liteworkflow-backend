package com.liteworkflow.ai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean OpenAPI aiOpenApi() { return api("ai"); }
    @Bean GroupedOpenApi aiGroup() {
        return GroupedOpenApi.builder().group("ai").pathsToMatch("/api/**").build();
    }
    private static OpenAPI api(String service) {
        return new OpenAPI().info(new Info().title("liteworkflow " + service + " API").version("v1"))
                .components(new Components().addSecuritySchemes("bearer-jwt", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
