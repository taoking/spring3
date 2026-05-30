package com.taoking.spring3.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GatewayRouteConfig {

    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayRoutesProperties properties) {
        return builder.routes()
                .route("catalog-route", route -> route
                        .path("/catalog/**")
                        .filters(filters -> filters
                                .stripPrefix(1)
                                .circuitBreaker(config -> config
                                        .setName("catalog-route")
                                        .setFallbackUri("forward:/fallback/catalog")))
                        .uri(properties.catalogUri()))
                .route("orders-canary-route", route -> route
                        .path("/orders/**")
                        .and()
                        .header("X-Canary", "true")
                        .filters(filters -> filters
                                .stripPrefix(1)
                                .circuitBreaker(config -> config
                                        .setName("orders-route")
                                        .setFallbackUri("forward:/fallback/orders")))
                        .uri(properties.orderCanaryUri()))
                .route("orders-route", route -> route
                        .path("/orders/**")
                        .filters(filters -> filters
                                .stripPrefix(1)
                                .circuitBreaker(config -> config
                                        .setName("orders-route")
                                        .setFallbackUri("forward:/fallback/orders")))
                        .uri(properties.orderUri()))
                .build();
    }
}
