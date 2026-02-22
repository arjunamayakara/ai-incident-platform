package com.aiplatform.consumer.kafka;

import com.aiplatform.common.model.Incident;
import com.aiplatform.common.model.MetricEvent;
import com.aiplatform.consumer.detector.AnomalyDetector;
import com.aiplatform.consumer.publisher.IncidentPublisher;
import com.aiplatform.consumer.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer - listens to metric-events topic,
 * runs anomaly detection, creates incidents, and
 * forwards to AI agent via incidents topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricEventConsumer {

    private final AnomalyDetector anomalyDetector;
    private final IncidentService incidentService;
    private final IncidentPublisher incidentPublisher;

    @KafkaListener(
            topics = "${kafka.topics.metrics}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(@Payload MetricEvent event, Acknowledgment ack) {
        try {
            log.debug("Received metric event: service={} metric={} value={}",
                    event.getServiceId(), event.getMetricType(), event.getValue());

            if (anomalyDetector.isAnomaly(event)) {
                String description = anomalyDetector.describeAnomaly(event);
                Incident incident = incidentService.createIncident(event, description);

                if (incident != null) {
                    log.warn("🚨 Anomaly → Incident created: id={} service={} severity={}",
                            incident.getIncidentId(), incident.getServiceId(), incident.getSeverity());
                    // Forward to AI agent
                    incidentPublisher.publishIncident(incident);
                }
            }

            // Acknowledge offset only after successful processing
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing metric event: service={} eventId={}",
                    event.getServiceId(), event.getEventId(), e);
            // Still acknowledge to avoid infinite retry loop for bad messages
            // In production, you'd send to a dead-letter topic instead
            ack.acknowledge();
        }
    }
}
