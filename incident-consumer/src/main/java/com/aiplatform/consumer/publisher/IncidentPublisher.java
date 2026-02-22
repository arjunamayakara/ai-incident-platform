package com.aiplatform.consumer.publisher;

import com.aiplatform.common.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentPublisher {

    private final KafkaTemplate<String, Object> defaultKafkaTemplate;

    @Value("${kafka.topics.incidents}")
    private String incidentsTopic;

    public void publishIncident(Incident incident) {
        defaultKafkaTemplate.send(incidentsTopic, incident.getServiceId(), incident)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish incident: {}", incident.getIncidentId(), ex);
                    } else {
                        log.info("Published incident to AI agent: id={}", incident.getIncidentId());
                    }
                });
    }
}
