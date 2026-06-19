package com.taoking.spring3.order.messaging.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@Profile("kafka")
@EnableConfigurationProperties(KafkaDemoProperties.class)
class KafkaDemoMessagingConfig {

    @Bean
    NewTopic kafkaDemoTopic(KafkaDemoProperties properties) {
        return topic(properties.topic(), properties);
    }

    @Bean
    NewTopic kafkaDemoRetryInputTopic(KafkaDemoProperties properties) {
        return topic(properties.retryInputTopic(), properties);
    }

    @Bean
    NewTopic kafkaDemoRetryTopic(KafkaDemoProperties properties) {
        return topic(properties.retryTopic(), properties);
    }

    @Bean
    NewTopic kafkaDemoRetryDltTopic(KafkaDemoProperties properties) {
        return topic(properties.retryDltTopic(), properties);
    }

    @Bean
    NewTopic kafkaDemoSchemaTopic(KafkaDemoProperties properties) {
        return topic(properties.schemaTopic(), properties);
    }

    @Bean
    NewTopic kafkaDemoTransactionInputTopic(KafkaDemoProperties properties) {
        return topic(properties.transactionInputTopic(), properties);
    }

    @Bean
    NewTopic kafkaDemoTransactionAuditTopic(KafkaDemoProperties properties) {
        return topic(properties.transactionAuditTopic(), properties);
    }

    @Bean
    NewTopic kafkaDemoLagTopic(KafkaDemoProperties properties) {
        return topic(properties.lagTopic(), properties);
    }

    @Bean(name = "kafkaDemoListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, KafkaDemoEvent> kafkaDemoListenerContainerFactory(
            KafkaProperties kafkaProperties,
            KafkaDemoState state
    ) {
        ConcurrentKafkaListenerContainerFactory<String, KafkaDemoEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaDemoConsumerFactory(kafkaProperties));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setConsumerRebalanceListener(rebalanceListener(state));
        return factory;
    }

    @Bean(name = "kafkaDemoStringListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaDemoStringListenerContainerFactory(
            KafkaProperties kafkaProperties,
            KafkaDemoState state
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaDemoStringConsumerFactory(kafkaProperties));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setConsumerRebalanceListener(rebalanceListener(state));
        return factory;
    }

    private NewTopic topic(String name, KafkaDemoProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    private ConsumerFactory<String, KafkaDemoEvent> kafkaDemoConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = demoConsumerProperties(kafkaProperties);
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, KafkaDemoEvent.class.getName());
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.taoking.spring3.order.messaging.kafka");
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>())
        );
    }

    private ConsumerFactory<String, String> kafkaDemoStringConsumerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(
                demoConsumerProperties(kafkaProperties),
                new StringDeserializer(),
                new StringDeserializer()
        );
    }

    private Map<String, Object> demoConsumerProperties(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return properties;
    }

    private ConsumerRebalanceListener rebalanceListener(KafkaDemoState state) {
        return new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(java.util.Collection<org.apache.kafka.common.TopicPartition> partitions) {
                state.recordRebalance("revoked", partitions);
            }

            @Override
            public void onPartitionsAssigned(java.util.Collection<org.apache.kafka.common.TopicPartition> partitions) {
                state.recordRebalance("assigned", partitions);
            }
        };
    }
}
