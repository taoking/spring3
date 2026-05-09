package com.taoking.spring3.order.config;

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
    OpenAPI orderOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .version("v1")
                        .description("Consumer service used by the Spring Boot 3 learning lab."))
                .schemaRequirement("basicAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic"))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }

    @Bean
    GroupedOpenApi orderV1ApiGroup() {
        return GroupedOpenApi.builder()
                .group("orders-v1")
                .pathsToMatch("/api/orders/**", "/api/v1/orders/**")
                .pathsToExclude("/api/orders/admin/**", "/api/orders/thread-probe")
                .build();
    }

    @Bean
    GroupedOpenApi orderV2ApiGroup() {
        return GroupedOpenApi.builder()
                .group("orders-v2")
                .pathsToMatch("/api/v2/orders/**")
                .build();
    }

    @Bean
    GroupedOpenApi orderOperationsApiGroup() {
        return GroupedOpenApi.builder()
                .group("orders-ops")
                .pathsToMatch("/api/orders/admin/**", "/api/orders/thread-probe")
                .build();
    }
}
