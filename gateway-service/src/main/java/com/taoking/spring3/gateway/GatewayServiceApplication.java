package com.taoking.spring3.gateway;

import com.taoking.spring3.gateway.config.GatewayRoutesProperties;
import com.taoking.spring3.gateway.config.LocalRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GatewayRoutesProperties.class, LocalRateLimitProperties.class})
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
