package com.aiplatform.producer.service;

import com.aiplatform.common.model.MetricEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricEventProducerService {

    private final KafkaTemplate<String, MetricEvent> kafkaTemplate;

    @Value("${kafka.topics.metrics}")
    private String metricsTopic;

    /**
     * Publishes a metric event to Kafka.
     * Uses serviceId as the partition key so all events from
     * the same service go to the same partition (ordering guarantee).
     */
    public void publishMetricEvent(MetricEvent event) {
        CompletableFuture<SendResult<String, MetricEvent>> future =
                kafkaTemplate.send(metricsTopic, event.getServiceId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish metric event for service={} eventId={}",
                        event.getServiceId(), event.getEventId(), ex);
            } else {
                log.debug("Published metric event: serviceId={} metricType={} value={} severity={} partition={}",
                        event.getServiceId(),
                        event.getMetricType(),
                        event.getValue(),
                        event.getSeverity(),
                        result.getRecordMetadata().partition());
            }
        });
    }
}
