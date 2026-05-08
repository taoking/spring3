package com.taoking.spring3.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "demo.gateway.rate-limit.enabled=false"
})
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
