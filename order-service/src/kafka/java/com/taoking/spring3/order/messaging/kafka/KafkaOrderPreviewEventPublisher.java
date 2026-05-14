package com.taoking.spring3.order.messaging.kafka;

import com.taoking.spring3.common.api.ApiHeaders;
import com.taoking.spring3.order.event.OrderPreviewCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Profile("kafka")
class KafkaOrderPreviewEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderPreviewEventPublisher.class);

    private final KafkaTemplate<String, OrderPreviewKafkaEvent> kafkaTemplate;
    private final KafkaOrderMessagingProperties properties;
    private final ObjectProvider<Tracer> tracerProvider;
    private final Counter publishedCounter;
    private final Counter sendFailureCounter;

    KafkaOrderPreviewEventPublisher(
            KafkaTemplate<String, OrderPreviewKafkaEvent> kafkaTemplate,
            KafkaOrderMessagingProperties properties,
            ObjectProvider<Tracer> tracerProvider,
            MeterRegistry meterRegistry
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.tracerProvider = tracerProvider;
        this.publishedCounter = Counter.builder("orders.preview.kafka.published.total")
                .description("Number of order preview events published to Kafka")
                .register(meterRegistry);
        this.sendFailureCounter = Counter.builder("orders.preview.kafka.send.failed.total")
                .description("Number of order preview events Kafka failed to accept")
                .register(meterRegistry);
    }

    @EventListener
    void onOrderPreviewCreated(OrderPreviewCreatedEvent event) {
        String requestId = resolveRequestId();
        String traceId = resolveTraceId();
        OrderPreviewKafkaEvent kafkaEvent = OrderPreviewKafkaEvent.from(event, requestId, traceId);
        ProducerRecord<String, OrderPreviewKafkaEvent> record = new ProducerRecord<>(
                properties.topic(),
                kafkaEvent.partitionKey(),
                kafkaEvent
        );
        addHeader(record, "eventId", kafkaEvent.eventId());
        addHeader(record, "eventType", kafkaEvent.eventType());
        addHeader(record, "eventVersion", Integer.toString(kafkaEvent.eventVersion()));
        addHeader(record, "source", kafkaEvent.source());
        addHeader(record, ApiHeaders.REQUEST_ID, requestId);
        addHeader(record, "traceId", traceId);
        addHeader(record, "traceparent", resolveTraceparent());

        kafkaTemplate.send(record).whenComplete((result, exception) -> handleSendResult(kafkaEvent, result, exception));
    }

    private void handleSendResult(
            OrderPreviewKafkaEvent event,
            SendResult<String, OrderPreviewKafkaEvent> result,
            Throwable exception
    ) {
        if (exception != null) {
            sendFailureCounter.increment();
            log.warn("Failed to publish order preview Kafka event eventId={} requestId={} traceId={} topic={} reason={}",
                    event.eventId(),
                    event.requestId(),
                    event.traceId(),
                    properties.topic(),
                    exception.getMessage());
            return;
        }
        publishedCounter.increment();
        log.info("Published order preview Kafka event eventId={} requestId={} traceId={} topic={} partition={} offset={}",
                event.eventId(),
                event.requestId(),
                event.traceId(),
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    private String resolveRequestId() {
        String requestId = currentRequestHeader(ApiHeaders.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = MDC.get("requestId");
        }
        return StringUtils.hasText(requestId) ? requestId : null;
    }

    private String resolveTraceId() {
        String traceIdFromHeader = traceIdFromTraceparent(resolveTraceparent());
        if (StringUtils.hasText(traceIdFromHeader)) {
            return traceIdFromHeader;
        }
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && StringUtils.hasText(currentSpan.context().traceId())) {
                return currentSpan.context().traceId();
            }
        }
        String traceId = MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return null;
    }

    private String resolveTraceparent() {
        return currentRequestHeader("traceparent");
    }

    private String currentRequestHeader(String headerName) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(headerName);
        }
        return null;
    }

    private String traceIdFromTraceparent(String traceparent) {
        if (!StringUtils.hasText(traceparent)) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length >= 2 ? parts[1] : null;
    }

    private void addHeader(ProducerRecord<String, OrderPreviewKafkaEvent> record, String name, String value) {
        if (StringUtils.hasText(value)) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
