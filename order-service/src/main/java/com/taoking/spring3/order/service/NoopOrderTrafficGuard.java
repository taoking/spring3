package com.taoking.spring3.order.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!sentinel")
class NoopOrderTrafficGuard implements OrderTrafficGuard {

    @Override
    public void checkPreviewFlow(boolean enabled) {
    }

    @Override
    public void checkHotSku(boolean enabled, String sku) {
    }
}
