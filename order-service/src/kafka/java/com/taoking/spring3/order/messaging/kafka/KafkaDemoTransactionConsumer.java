package com.taoking.spring3.order.messaging.kafka;

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
class KafkaDemoTransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaDemoTransactionConsumer.class);

    private final KafkaDemoProperties properties;
    private final KafkaDemoPublisher publisher;
    private final KafkaDemoState state;

    KafkaDemoTransactionConsumer(
            KafkaDemoProperties properties,
            KafkaDemoPublisher publisher,
            KafkaDemoState state
    ) {
        this.properties = properties;
        this.publisher = publisher;
        this.state = state;
    }

    @KafkaListener(
            id = "kafkaDemoTransactionInputListener",
            topics = "${demo.kafka.examples.transaction-input-topic}",
            groupId = "${demo.kafka.examples.transaction-consumer-group}",
            containerFactory = "kafkaDemoListenerContainerFactory"
    )
    void onTransactionInput(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) {
        state.recordTransactionInput(event, record);
        KafkaDemoEvent auditEvent = event.withScenario(
                "TRANSACTION_AUDIT",
                Map.of("auditDescription", "audit event was produced with a transactional KafkaTemplate")
        );
        publisher.sendAuditInTransaction(properties.transactionAuditTopic(), auditEvent);
        acknowledgment.acknowledge();
        log.info("Kafka transaction demo consumed input and wrote audit eventId={} inputTopic={} auditTopic={}",
                event.eventId(),
                record.topic(),
                properties.transactionAuditTopic());
    }

    @KafkaListener(
            id = "kafkaDemoTransactionAuditListener",
            topics = "${demo.kafka.examples.transaction-audit-topic}",
            groupId = "${demo.kafka.examples.transaction-consumer-group}",
            containerFactory = "kafkaDemoListenerContainerFactory"
    )
    void onTransactionAudit(
            KafkaDemoEvent event,
            ConsumerRecord<String, KafkaDemoEvent> record,
            Acknowledgment acknowledgment
    ) {
        state.recordTransactionAudit(event, record);
        acknowledgment.acknowledge();
        log.info("Kafka transaction audit demo consumed eventId={} topic={} partition={} offset={}",
                event.eventId(),
                record.topic(),
                record.partition(),
                record.offset());
    }
}
