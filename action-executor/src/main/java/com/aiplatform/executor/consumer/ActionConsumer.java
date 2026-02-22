package com.aiplatform.executor.consumer;

import com.aiplatform.common.model.Incident;
import com.aiplatform.executor.service.ActionDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionConsumer {

    private final ActionDispatcherService dispatcherService;

    @KafkaListener(
            topics = "${kafka.topics.actions}",
            groupId = "action-executor-group",
            containerFactory = "actionListenerFactory"
    )
    public void consume(@Payload Incident incident, Acknowledgment ack) {
        log.info("Action Executor received incident: id={} service={} severity={}",
                incident.getIncidentId(), incident.getServiceId(), incident.getSeverity());

        try {
            dispatcherService.dispatch(incident);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to execute action for incident: {}", incident.getIncidentId(), e);
            ack.acknowledge(); // acknowledge to avoid infinite loop
        }
    }
}
