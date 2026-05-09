package com.taoking.spring3.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI catalogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Catalog Service API")
                        .version("v1")
                        .description("Provider service used by the Spring Boot 3 learning lab."))
                .schemaRequirement("basicAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic"))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }

    @Bean
    GroupedOpenApi catalogPublicApiGroup() {
        return GroupedOpenApi.builder()
                .group("catalog-public")
                .pathsToMatch("/api/catalog/products/**")
                .build();
    }

    @Bean
    GroupedOpenApi catalogAdminApiGroup() {
        return GroupedOpenApi.builder()
                .group("catalog-admin")
                .pathsToMatch("/api/catalog/admin/**")
                .build();
    }
}
