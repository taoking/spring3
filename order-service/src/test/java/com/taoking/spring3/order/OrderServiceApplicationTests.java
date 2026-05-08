package com.taoking.spring3.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "demo.clients.catalog.base-url=http://localhost:65535",
        "demo.order.heartbeat-delay=PT1H"
})
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
