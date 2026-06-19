package com.taoking.spring3.order.messaging.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
class KafkaDemoRetryTopicConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaDemoRetryTopicConsumer.class);

    private final KafkaDemoProperties properties;
    private final KafkaDemoPublisher publisher;
    private final KafkaDemoState state;

    KafkaDemoRetryTopicConsumer(
            KafkaDemoProperties properties,
            KafkaDemoPublisher publisher,
            KafkaDemoState state
    ) {
        this.properties = properties;
        this.publisher = publisher;
        this.state = state;
    }

    @KafkaListener(
            id = "kafkaDemoRetryInputListener",
            topics = "${demo.kafka.examples.retry-input-topic}",
            groupId = "${demo.kafka.examples.retry-consumer-group}",
            containerFactory = "kafkaDemoListenerContainerFactory"
    )
    void onRetryInput(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) {
        handleRetry(event, record, acknowledgment);
    }

    @KafkaListener(
            id = "kafkaDemoRetryTopicListener",
            topics = "${demo.kafka.examples.retry-topic}",
            groupId = "${demo.kafka.examples.retry-consumer-group}",
            containerFactory = "kafkaDemoListenerContainerFactory"
    )
    void onRetryTopic(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) {
        handleRetry(event, record, acknowledgment);
    }

    @KafkaListener(
            id = "kafkaDemoRetryDltListener",
            topics = "${demo.kafka.examples.retry-dlt-topic}",
            groupId = "${demo.kafka.examples.retry-consumer-group}",
            containerFactory = "kafkaDemoListenerContainerFactory"
    )
    void onRetryDlt(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) {
        state.recordRetryDlt(event, record);
        acknowledgment.acknowledge();
        log.warn("Kafka retry topic demo reached DLT eventId={} topic={} partition={} offset={}",
                event.eventId(),
                record.topic(),
                record.partition(),
                record.offset());
    }

    private void handleRetry(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) {
        int currentAttempt = retryAttempt(record);
        int nextAttempt = currentAttempt + 1;
        int failUntilAttempt = failUntilAttempt(event);
        state.recordRetryAttempt(event, nextAttempt, record.topic());
        if (nextAttempt <= Math.min(failUntilAttempt, properties.retryMaxAttempts())) {
            publisher.sendWithRetryAttempt(properties.retryTopic(), event, nextAttempt, record.topic());
            acknowledgment.acknowledge();
            log.info("Kafka retry topic demo scheduled retry eventId={} attempt={} fromTopic={} retryTopic={}",
                    event.eventId(),
                    nextAttempt,
                    record.topic(),
                    properties.retryTopic());
            return;
        }
        publisher.sendWithRetryAttempt(properties.retryDltTopic(), event, nextAttempt, record.topic());
        acknowledgment.acknowledge();
        log.warn("Kafka retry topic demo sent eventId={} to retry DLT after attempt={}",
                event.eventId(),
                nextAttempt);
    }

    private int retryAttempt(ConsumerRecord<String, KafkaDemoEvent> record) {
        Header header = record.headers().lastHeader(KafkaDemoPublisher.RETRY_ATTEMPT_HEADER);
        if (header == null) {
            return 0;
        }
        return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
    }

    private int failUntilAttempt(KafkaDemoEvent event) {
        Object value = event.payload().get("failUntilAttempt");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return properties.retryMaxAttempts();
    }
}
