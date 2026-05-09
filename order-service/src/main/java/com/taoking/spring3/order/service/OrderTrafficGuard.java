package com.taoking.spring3.order.service;

public interface OrderTrafficGuard {

    void checkPreviewFlow(boolean enabled);

    void checkHotSku(boolean enabled, String sku);
}
