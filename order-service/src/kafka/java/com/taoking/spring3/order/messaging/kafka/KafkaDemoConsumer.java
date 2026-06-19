package com.taoking.spring3.order.messaging.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
class KafkaDemoConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaDemoConsumer.class);

    private final KafkaDemoProperties properties;
    private final KafkaDemoState state;

    KafkaDemoConsumer(KafkaDemoProperties properties, KafkaDemoState state) {
        this.properties = properties;
        this.state = state;
    }

    @KafkaListener(
            id = "kafkaDemoEventsListener",
            topics = "${demo.kafka.examples.topic}",
            groupId = "${demo.kafka.examples.consumer-group}",
            containerFactory = "kafkaDemoListenerContainerFactory"
    )
    void onDemoEvent(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) {
        boolean first = state.markConsumed(event, record);
        acknowledgment.acknowledge();
        log.info("Kafka demo consumed scenario={} eventId={} first={} topic={} partition={} offset={} group={}",
                event.scenario(),
                event.eventId(),
                first,
                record.topic(),
                record.partition(),
                record.offset(),
                properties.consumerGroup());
    }

    @KafkaListener(
            id = "kafkaDemoLagListener",
            topics = "${demo.kafka.examples.lag-topic}",
            groupId = "${demo.kafka.examples.lag-consumer-group}",
            containerFactory = "kafkaDemoListenerContainerFactory"
    )
    void onLagEvent(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) throws InterruptedException {
        long delay = Math.min(event.processingDelayMs(), properties.maxProcessingDelay().toMillis());
        if (delay > 0) {
            Thread.sleep(delay);
        }
        state.recordLagProcessed(event, record);
        acknowledgment.acknowledge();
        log.info("Kafka lag demo consumed eventId={} delayMs={} topic={} partition={} offset={} group={}",
                event.eventId(),
                delay,
                record.topic(),
                record.partition(),
                record.offset(),
                properties.lagConsumerGroup());
    }
}
