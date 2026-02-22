package com.aiplatform.agent.consumer;

import com.aiplatform.agent.service.AiDiagnosisResponse;
import com.aiplatform.agent.service.IncidentDiagnosisService;
import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes incidents → runs AI diagnosis → publishes enriched incident to actions topic.
 *
 * Flow:
 * incidents topic → [AI Agent] → actions topic
 *                       ↓
 *                   Mistral LLM (via Ollama)
 *                   root cause + recommendation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentAgentConsumerNoRunBook {

    private final IncidentDiagnosisService diagnosisService;
    private final KafkaTemplate<String, Object> agentKafkaTemplate;

    @Value("${kafka.topics.actions}")
    private String actionsTopic;

    @KafkaListener(
            topics = "${kafka.topics.incidents}",
            groupId = "ai-agent-group",
            containerFactory = "incidentListenerFactory"
    )
    public void consume(@Payload Incident incident, Acknowledgment ack) {
        log.info(" AI Agent received incident: id={} service={} severity={}",
                incident.getIncidentId(), incident.getServiceId(), incident.getSeverity());

        try {
            // Mark as analyzing
            incident.setStatus(IncidentStatus.ANALYZING);

            // Call Mistral
            AiDiagnosisResponse diagnosis = diagnosisService.diagnose(incident);

            // Enrich the incident with AI findings
            incident.setAiRootCause(diagnosis.getRootCause());
            incident.setAiRecommendation(diagnosis.getRecommendation()
                    + " | Suggested action: " + diagnosis.getAction()
                    + " | Impact: " + diagnosis.getImpact());
            incident.setStatus(IncidentStatus.REMEDIATING);

            log.info(" AI diagnosis complete for incident {}:\n" +
                            "   Root Cause: {}\n" +
                            "   Impact: {}\n" +
                            "   Recommendation: {}\n" +
                            "   Action: {}",
                    incident.getIncidentId(),
                    diagnosis.getRootCause(),
                    diagnosis.getImpact(),
                    diagnosis.getRecommendation(),
                    diagnosis.getAction());

            // Forward to action executor
            agentKafkaTemplate.send(actionsTopic, incident.getServiceId(), incident);
            log.info(" Forwarded to action executor: incidentId={} action={}",
                    incident.getIncidentId(), diagnosis.getAction());

            ack.acknowledge();

        } catch (Exception e) {
            log.error(" AI diagnosis failed for incident: {}", incident.getIncidentId(), e);
            incident.setStatus(IncidentStatus.ESCALATED);
            incident.setAiRootCause("AI diagnosis failed: " + e.getMessage());
            incident.setAiRecommendation("Manual investigation required");
            agentKafkaTemplate.send(actionsTopic, incident.getServiceId(), incident);
            ack.acknowledge();
        }
    }
}