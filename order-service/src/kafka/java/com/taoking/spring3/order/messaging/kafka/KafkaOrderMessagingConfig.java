package com.taoking.spring3.order.messaging.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@Profile("kafka")
@EnableConfigurationProperties(KafkaOrderMessagingProperties.class)
class KafkaOrderMessagingConfig {

    @Bean
    NewTopic orderPreviewKafkaTopic(KafkaOrderMessagingProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    NewTopic orderPreviewKafkaRetryTopic(KafkaOrderMessagingProperties properties) {
        return TopicBuilder.name(properties.retryTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    NewTopic orderPreviewKafkaDeadLetterTopic(KafkaOrderMessagingProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    DefaultErrorHandler kafkaOrderPreviewErrorHandler(
            KafkaTemplate<String, OrderPreviewKafkaEvent> kafkaTemplate,
            KafkaOrderMessagingProperties properties
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition())
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(properties.retryBackoff().toMillis(), properties.retryMaxAttempts())
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    @Bean(name = "kafkaOrderPreviewListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, OrderPreviewKafkaEvent> kafkaOrderPreviewListenerContainerFactory(
            ConsumerFactory<String, OrderPreviewKafkaEvent> consumerFactory,
            DefaultErrorHandler kafkaOrderPreviewErrorHandler,
            KafkaOrderMessagingProperties properties
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OrderPreviewKafkaEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.listenerConcurrency());
        factory.setCommonErrorHandler(kafkaOrderPreviewErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
