package com.taoking.spring3.order.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
class KafkaDemoPublisher {

    static final String RETRY_ATTEMPT_HEADER = "demo-retry-attempt";
    static final String ORIGINAL_TOPIC_HEADER = "demo-original-topic";

    private final KafkaTemplate<String, KafkaDemoEvent> kafkaTemplate;
    private final KafkaTemplate<String, String> stringTemplate;
    private final KafkaTemplate<String, KafkaDemoEvent> transactionalTemplate;
    private final ObjectMapper objectMapper;

    KafkaDemoPublisher(
            @Qualifier("kafkaTemplate") KafkaTemplate<String, KafkaDemoEvent> kafkaTemplate,
            KafkaProperties kafkaProperties,
            KafkaDemoProperties demoProperties,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.stringTemplate = stringTemplate(kafkaProperties);
        this.transactionalTemplate = transactionalTemplate(kafkaProperties, demoProperties);
        this.objectMapper = objectMapper;
    }

    void send(String topic, KafkaDemoEvent event) {
        send(record(topic, event, null, null));
    }

    void sendWithRetryAttempt(String topic, KafkaDemoEvent event, int attempt, String originalTopic) {
        ProducerRecord<String, KafkaDemoEvent> record = record(topic, event, attempt, originalTopic);
        send(record);
    }

    String sendSchemaV2(String topic, String eventId, String partitionKey) {
        Map<String, Object> schemaEvent = Map.of(
                "eventId", eventId,
                "eventType", "KafkaDemoSchemaChanged",
                "eventVersion", 2,
                "source", KafkaDemoEvent.SOURCE,
                "partitionKey", partitionKey,
                "payload", Map.of(
                        "sku", "SKU-SCHEMA",
                        "quantity", 2,
                        "channel", "mobile",
                        "newOptionalField", "old consumers ignore this field"
                )
        );
        try {
            stringTemplate.send(topic, partitionKey, objectMapper.writeValueAsString(schemaEvent))
                    .get(10, TimeUnit.SECONDS);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Kafka schema demo event", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish Kafka schema demo event", ex);
        }
        return eventId;
    }

    void sendCommittedTransaction(String topic, KafkaDemoEvent event) {
        transactionalTemplate.executeInTransaction(operations -> {
            operations.send(topic, event.partitionKey(), event);
            return true;
        });
    }

    void sendAbortedTransaction(String topic, KafkaDemoEvent event) {
        try {
            transactionalTemplate.executeInTransaction(operations -> {
                operations.send(topic, event.partitionKey(), event);
                throw new IllegalStateException("Intentional Kafka transaction rollback demo");
            });
        } catch (IllegalStateException ex) {
            if (!"Intentional Kafka transaction rollback demo".equals(ex.getMessage())) {
                throw ex;
            }
        }
    }

    void sendAuditInTransaction(String topic, KafkaDemoEvent event) {
        transactionalTemplate.executeInTransaction(operations -> {
            operations.send(topic, event.partitionKey(), event);
            return true;
        });
    }

    private ProducerRecord<String, KafkaDemoEvent> record(
            String topic,
            KafkaDemoEvent event,
            Integer retryAttempt,
            String originalTopic
    ) {
        ProducerRecord<String, KafkaDemoEvent> record = new ProducerRecord<>(topic, event.partitionKey(), event);
        record.headers().add("eventId", event.eventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("scenario", event.scenario().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventVersion", Integer.toString(event.eventVersion()).getBytes(StandardCharsets.UTF_8));
        if (retryAttempt != null) {
            record.headers().add(RETRY_ATTEMPT_HEADER, Integer.toString(retryAttempt).getBytes(StandardCharsets.UTF_8));
        }
        if (originalTopic != null) {
            record.headers().add(ORIGINAL_TOPIC_HEADER, originalTopic.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private void send(ProducerRecord<String, KafkaDemoEvent> record) {
        try {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to publish Kafka demo event to topic=" + record.topic(), ex);
        }
    }

    private KafkaTemplate<String, String> stringTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    private KafkaTemplate<String, KafkaDemoEvent> transactionalTemplate(
            KafkaProperties kafkaProperties,
            KafkaDemoProperties demoProperties
    ) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        DefaultKafkaProducerFactory<String, KafkaDemoEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(properties);
        producerFactory.setTransactionIdPrefix(demoProperties.transactionIdPrefix());
        return new KafkaTemplate<>(producerFactory);
    }
}
