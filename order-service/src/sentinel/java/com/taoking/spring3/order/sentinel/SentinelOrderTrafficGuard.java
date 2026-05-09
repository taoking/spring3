package com.taoking.spring3.order.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.taoking.spring3.order.service.OrderTrafficGuard;
import com.taoking.spring3.order.service.SentinelBlockedException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("sentinel")
class SentinelOrderTrafficGuard implements OrderTrafficGuard {

    private final Duration degradeProbeDelay;

    SentinelOrderTrafficGuard(@Value("${demo.sentinel.degrade.probe-delay:50ms}") Duration degradeProbeDelay) {
        this.degradeProbeDelay = degradeProbeDelay;
    }

    @Override
    public void checkPreviewFlow(boolean enabled) {
        if (!enabled) {
            return;
        }
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelResources.PREVIEW_FLOW);
        } catch (BlockException ex) {
            throw new SentinelBlockedException(SentinelResources.PREVIEW_FLOW, "FLOW", ex);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    @Override
    public void checkHotSku(boolean enabled, String sku) {
        if (!enabled) {
            return;
        }
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelResources.HOT_SKU, EntryType.IN, 1, sku);
        } catch (BlockException ex) {
            throw new SentinelBlockedException(SentinelResources.HOT_SKU, "HOT_PARAM", ex);
        } finally {
            if (entry != null) {
                entry.exit(1, sku);
            }
        }
    }

    SentinelProbeResponse checkDegradeProbe(boolean slow) {
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelResources.DEGRADE_PROBE);
            if (slow) {
                sleep(degradeProbeDelay);
                return new SentinelProbeResponse(SentinelResources.DEGRADE_PROBE, "slow-call", true);
            }
            return new SentinelProbeResponse(SentinelResources.DEGRADE_PROBE, "allowed", false);
        } catch (BlockException ex) {
            throw new SentinelBlockedException(SentinelResources.DEGRADE_PROBE, "DEGRADE", ex);
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sentinel degrade probe was interrupted", ex);
        }
    }
}
