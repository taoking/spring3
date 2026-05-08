package com.taoking.spring3.order.config;

import feign.RequestInterceptor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FeignConfig {

    @Bean
    RequestInterceptor catalogBasicAuthInterceptor(CatalogClientProperties properties) {
        String token = Base64.getEncoder().encodeToString(
                (properties.username() + ":" + properties.password()).getBytes(StandardCharsets.UTF_8)
        );
        return requestTemplate -> requestTemplate.header("Authorization", "Basic " + token);
    }
}
