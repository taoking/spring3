package com.taoking.spring3.order.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("sentinel")
class SentinelRuleConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelRuleConfig.class);

    private final double previewFlowQps;
    private final double hotSkuQps;
    private final Duration hotSkuDuration;
    private final Duration degradeSlowThreshold;
    private final double degradeSlowRatioThreshold;
    private final int degradeMinimumRequestAmount;
    private final Duration degradeStatInterval;
    private final Duration degradeTimeWindow;

    SentinelRuleConfig(
            @Value("${demo.sentinel.flow.qps:1}") double previewFlowQps,
            @Value("${demo.sentinel.hot-sku.qps:1}") double hotSkuQps,
            @Value("${demo.sentinel.hot-sku.duration:1s}") Duration hotSkuDuration,
            @Value("${demo.sentinel.degrade.slow-threshold:10ms}") Duration degradeSlowThreshold,
            @Value("${demo.sentinel.degrade.slow-ratio-threshold:0.5}") double degradeSlowRatioThreshold,
            @Value("${demo.sentinel.degrade.minimum-request-amount:2}") int degradeMinimumRequestAmount,
            @Value("${demo.sentinel.degrade.stat-interval:1s}") Duration degradeStatInterval,
            @Value("${demo.sentinel.degrade.time-window:5s}") Duration degradeTimeWindow
    ) {
        this.previewFlowQps = previewFlowQps;
        this.hotSkuQps = hotSkuQps;
        this.hotSkuDuration = hotSkuDuration;
        this.degradeSlowThreshold = degradeSlowThreshold;
        this.degradeSlowRatioThreshold = degradeSlowRatioThreshold;
        this.degradeMinimumRequestAmount = degradeMinimumRequestAmount;
        this.degradeStatInterval = degradeStatInterval;
        this.degradeTimeWindow = degradeTimeWindow;
    }

    @PostConstruct
    void loadRules() {
        FlowRule previewFlowRule = new FlowRule(SentinelResources.PREVIEW_FLOW)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(previewFlowQps);
        FlowRuleManager.loadRules(List.of(previewFlowRule));

        ParamFlowRule hotSkuRule = new ParamFlowRule(SentinelResources.HOT_SKU)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setParamIdx(0)
                .setCount(hotSkuQps)
                .setDurationInSec(Math.max(1, hotSkuDuration.toSeconds()));
        ParamFlowRuleManager.loadRules(List.of(hotSkuRule));

        DegradeRule degradeRule = new DegradeRule(SentinelResources.DEGRADE_PROBE)
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                .setCount(Math.max(1, degradeSlowThreshold.toMillis()))
                .setSlowRatioThreshold(degradeSlowRatioThreshold)
                .setMinRequestAmount(degradeMinimumRequestAmount)
                .setStatIntervalMs(Math.toIntExact(Math.max(1000, degradeStatInterval.toMillis())))
                .setTimeWindow(Math.toIntExact(Math.max(1, degradeTimeWindow.toSeconds())));
        DegradeRuleManager.loadRules(List.of(degradeRule));

        log.info("Loaded Sentinel rules flowQps={} hotSkuQps={} degradeSlowThreshold={} degradeWindow={}",
                previewFlowQps,
                hotSkuQps,
                degradeSlowThreshold,
                degradeTimeWindow);
    }
}
