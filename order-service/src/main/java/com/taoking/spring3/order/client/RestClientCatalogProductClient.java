package com.taoking.spring3.order.client;

import com.taoking.spring3.common.dto.ProductResponse;
import com.taoking.spring3.order.config.CatalogClientProperties;
import com.taoking.spring3.order.config.CatalogClientProperties.CatalogClientMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "demo.clients.catalog", name = "mode", havingValue = "restclient")
class RestClientCatalogProductClient implements CatalogProductClient {

    private final RestClient restClient;
    private final CatalogFallbackSupport fallbackSupport;

    RestClientCatalogProductClient(
            RestClient.Builder restClientBuilder,
            CatalogClientProperties properties,
            CatalogFallbackSupport fallbackSupport
    ) {
        Assert.hasText(properties.baseUrl(), "demo.clients.catalog.base-url must be set when mode=restclient");
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password()))
                .build();
        this.fallbackSupport = fallbackSupport;
    }

    @Override
    public CatalogClientMode mode() {
        return CatalogClientMode.RESTCLIENT;
    }

    @Override
    public ProductResponse getProduct(String sku, boolean slow, boolean fail) {
        try {
            ProductResponse product = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/catalog/products/{sku}")
                            .queryParam("slow", slow)
                            .queryParam("fail", fail)
                            .build(sku))
                    .retrieve()
                    .body(ProductResponse.class);
            return product == null
                    ? fallbackSupport.fallbackProduct(sku, new IllegalStateException("Catalog returned empty body"))
                    : product;
        } catch (RestClientException ex) {
            return fallbackSupport.fallbackProduct(sku, ex);
        }
    }

    private ClientHttpRequestFactory requestFactory(CatalogClientProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return ClientHttpRequestFactoryBuilder.detect().build(settings);
    }
}
