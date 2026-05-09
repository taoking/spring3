package com.taoking.spring3.catalog.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "management.zipkin.tracing.export.enabled=false")
@AutoConfigureMockMvc
public abstract class CatalogContractBase {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setupRestAssuredMockMvc() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }
}
