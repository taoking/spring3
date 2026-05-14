package com.taoking.spring3.order.messaging.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "demo.messaging.kafka")
record KafkaOrderMessagingProperties(
        String topic,
        String retryTopic,
        String deadLetterTopic,
        String consumerGroup,
        String poisonSku,
        int partitions,
        int replicationFactor,
        int listenerConcurrency,
        Duration retryBackoff,
        long retryMaxAttempts
) {

    KafkaOrderMessagingProperties {
        topic = defaultIfBlank(topic, "spring3.order-preview.events.v1");
        retryTopic = defaultIfBlank(retryTopic, "spring3.order-preview.retry.v1");
        deadLetterTopic = defaultIfBlank(deadLetterTopic, "spring3.order-preview.dlt.v1");
        consumerGroup = defaultIfBlank(consumerGroup, "spring3-order-preview");
        poisonSku = defaultIfBlank(poisonSku, "SKU-KAFKA-FAIL");
        partitions = partitions > 0 ? partitions : 3;
        replicationFactor = replicationFactor > 0 ? replicationFactor : 1;
        listenerConcurrency = listenerConcurrency > 0 ? listenerConcurrency : partitions;
        retryBackoff = retryBackoff != null ? retryBackoff : Duration.ofMillis(100);
        retryMaxAttempts = retryMaxAttempts >= 0 ? retryMaxAttempts : 1;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
