package com.taoking.spring3.order.messaging.kafka;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("kafka")
@RequestMapping("/api/kafka-demo")
class KafkaDemoController {

    private final KafkaDemoScenarioService scenarios;

    KafkaDemoController(KafkaDemoScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @PostMapping("/basic")
    KafkaDemoEvent basic(@RequestParam(defaultValue = "demo-basic") String key) {
        return scenarios.publishBasic(key);
    }

    @PostMapping("/duplicates")
    List<KafkaDemoEvent> duplicates(
            @RequestParam(defaultValue = "") String eventId,
            @RequestParam(defaultValue = "demo-duplicate") String key
    ) {
        String resolvedEventId = eventId == null || eventId.isBlank() ? "duplicate-" + UUID.randomUUID() : eventId;
        return scenarios.publishDuplicate(resolvedEventId, key);
    }

    @PostMapping("/ordered")
    List<KafkaDemoEvent> ordered(
            @RequestParam(defaultValue = "demo-order") String key,
            @RequestParam(defaultValue = "3") int count
    ) {
        return scenarios.publishOrdered(key, count);
    }

    @PostMapping("/retry-topic")
    KafkaDemoEvent retryTopic(
            @RequestParam(defaultValue = "demo-retry") String key,
            @RequestParam(defaultValue = "2") int failUntilAttempt
    ) {
        return scenarios.publishRetryTopic(key, failUntilAttempt);
    }

    @PostMapping("/lag")
    List<KafkaDemoEvent> lag(
            @RequestParam(defaultValue = "demo-lag") String key,
            @RequestParam(defaultValue = "20") int count,
            @RequestParam(defaultValue = "200") long processingDelayMs
    ) {
        return scenarios.publishLag(key, count, processingDelayMs);
    }

    @PostMapping("/schema-v2")
    Map<String, Object> schemaV2(@RequestParam(defaultValue = "demo-schema") String key) {
        return Map.of("eventId", scenarios.publishSchemaV2(key), "eventVersion", 2);
    }

    @PostMapping("/transaction/commit")
    KafkaDemoEvent committedTransaction(@RequestParam(defaultValue = "demo-tx") String key) {
        return scenarios.publishCommittedTransaction(key);
    }

    @PostMapping("/transaction/abort")
    KafkaDemoEvent abortedTransaction(@RequestParam(defaultValue = "demo-tx") String key) {
        return scenarios.publishAbortedTransaction(key);
    }

    @GetMapping("/state")
    Map<String, Object> state() {
        return scenarios.state();
    }

    @PostMapping("/state/reset")
    Map<String, Object> resetState() {
        scenarios.reset();
        return scenarios.state();
    }

    @GetMapping("/security-template")
    Map<String, Object> securityTemplate() {
        return scenarios.securityTemplate();
    }

    @GetMapping("/capacity-plan")
    Map<String, Object> capacityPlan(
            @RequestParam(defaultValue = "1000") int peakMessagesPerSecond,
            @RequestParam(defaultValue = "20") int consumerMessageCostMs,
            @RequestParam(defaultValue = "500") int targetPartitionThroughput
    ) {
        return scenarios.capacityPlan(peakMessagesPerSecond, consumerMessageCostMs, targetPartitionThroughput);
    }

    @GetMapping("/selection-matrix")
    Map<String, Object> selectionMatrix() {
        return scenarios.selectionMatrix();
    }
}
