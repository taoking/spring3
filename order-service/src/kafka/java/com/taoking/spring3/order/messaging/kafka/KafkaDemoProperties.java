package com.taoking.spring3.order.messaging.kafka;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("demo.kafka.examples")
record KafkaDemoProperties(
        String topic,
        String retryInputTopic,
        String retryTopic,
        String retryDltTopic,
        String schemaTopic,
        String transactionInputTopic,
        String transactionAuditTopic,
        String lagTopic,
        String consumerGroup,
        String retryConsumerGroup,
        String schemaConsumerGroup,
        String transactionConsumerGroup,
        String lagConsumerGroup,
        int partitions,
        short replicationFactor,
        int retryMaxAttempts,
        Duration maxProcessingDelay,
        String transactionIdPrefix
) {
}
