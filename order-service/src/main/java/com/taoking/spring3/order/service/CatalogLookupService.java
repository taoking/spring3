package com.taoking.spring3.order.service;

import com.taoking.spring3.common.dto.ProductResponse;
import com.taoking.spring3.order.client.CatalogProductClient;
import com.taoking.spring3.order.config.CatalogClientProperties;
import com.taoking.spring3.order.config.CatalogClientProperties.CatalogClientMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CatalogLookupService {

    private final CatalogClientProperties properties;
    private final Map<CatalogClientMode, CatalogProductClient> clients;

    public CatalogLookupService(CatalogClientProperties properties, List<CatalogProductClient> catalogProductClients) {
        this.properties = properties;
        this.clients = new EnumMap<>(CatalogClientMode.class);
        catalogProductClients.forEach(client -> clients.put(client.mode(), client));
    }

    @Cacheable(cacheNames = "catalogProducts", key = "#root.target.cacheKey(#sku, #slow, #fail)", unless = "#result.fallback()")
    public ProductResponse getProduct(String sku, boolean slow, boolean fail) {
        CatalogProductClient client = clients.get(properties.mode());
        if (client == null) {
            throw new IllegalStateException("No catalog client for mode " + properties.mode());
        }
        return client.getProduct(sku, slow, fail);
    }

    public String cacheKey(String sku, boolean slow, boolean fail) {
        return properties.mode() + ":" + sku + ":" + slow + ":" + fail;
    }
}
