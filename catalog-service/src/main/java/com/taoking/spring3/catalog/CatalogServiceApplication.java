package com.taoking.spring3.catalog;

import com.taoking.spring3.catalog.config.CatalogNativeRuntimeHints;
import com.taoking.spring3.catalog.config.CatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@EnableConfigurationProperties(CatalogProperties.class)
@ImportRuntimeHints(CatalogNativeRuntimeHints.class)
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
