package com.taoking.spring3.order.messaging.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
class KafkaDemoSchemaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaDemoSchemaConsumer.class);
    private static final Set<String> V1_FIELDS = Set.of("sku", "quantity");

    private final ObjectMapper objectMapper;
    private final KafkaDemoState state;

    KafkaDemoSchemaConsumer(ObjectMapper objectMapper, KafkaDemoState state) {
        this.objectMapper = objectMapper;
        this.state = state;
    }

    @KafkaListener(
            id = "kafkaDemoSchemaListener",
            topics = "${demo.kafka.examples.schema-topic}",
            groupId = "${demo.kafka.examples.schema-consumer-group}",
            containerFactory = "kafkaDemoStringListenerContainerFactory"
    )
    void onSchemaEvent(
            String rawEvent,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) throws Exception {
        JsonNode root = objectMapper.readTree(rawEvent);
        String eventId = root.path("eventId").asText();
        int eventVersion = root.path("eventVersion").asInt();
        String partitionKey = root.path("partitionKey").asText();
        List<String> ignoredFields = ignoredPayloadFields(root.path("payload"));
        state.recordSchemaAccepted(eventId, eventVersion, partitionKey, ignoredFields);
        acknowledgment.acknowledge();
        log.info("Kafka schema demo accepted eventId={} eventVersion={} ignoredFields={} topic={} partition={} offset={}",
                eventId,
                eventVersion,
                ignoredFields,
                record.topic(),
                record.partition(),
                record.offset());
    }

    private List<String> ignoredPayloadFields(JsonNode payload) {
        List<String> ignored = new ArrayList<>();
        Iterator<String> names = payload.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!V1_FIELDS.contains(name)) {
                ignored.add(name);
            }
        }
        return ignored;
    }
}
