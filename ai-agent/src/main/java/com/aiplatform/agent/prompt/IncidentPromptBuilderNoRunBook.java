package com.aiplatform.agent.prompt;

import com.aiplatform.common.model.Incident;
import com.aiplatform.common.model.MetricEvent;
import org.springframework.stereotype.Component;

/**
 * Builds structured prompts for the LLM.
 *
 * Good prompt engineering is critical here —
 * we give the model clear context, a specific role,
 * and ask for structured output so we can parse it reliably.
 */
@Component
public class IncidentPromptBuilderNoRunBook {

    public String buildDiagnosisPrompt(Incident incident) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert Site Reliability Engineer (SRE).\n");
        sb.append("Analyze this production incident and reply in EXACTLY this format.\n");
        sb.append("Do not add any introduction, explanation, or text outside these 4 lines:\n\n");

        sb.append("ROOT_CAUSE: <one sentence root cause>\n");
        sb.append("IMPACT: <one sentence business impact>\n");
        sb.append("RECOMMENDATION: <one concrete remediation step>\n");
        sb.append("ACTION: <one of: RESTART_POD | SCALE_UP | ROLLBACK | ALERT_TEAM | INVESTIGATE>\n\n");

        sb.append("INCIDENT:\n");
        sb.append("Service: ").append(incident.getServiceId()).append("\n");
        sb.append("Instance: ").append(incident.getInstanceId()).append("\n");
        sb.append("Severity: ").append(incident.getSeverity()).append("\n");
        sb.append("Description: ").append(incident.getDescription()).append("\n");

        if (incident.getTriggerEvents() != null && !incident.getTriggerEvents().isEmpty()) {
            sb.append("Metrics:\n");
            for (MetricEvent event : incident.getTriggerEvents()) {
                sb.append(String.format("  - %s: %.2f (threshold: %.2f)\n",
                        event.getMetricType(),
                        event.getValue(),
                        event.getThreshold()));
            }
        }

        sb.append("\nRemember: reply with ONLY the 4 lines starting with ROOT_CAUSE:, IMPACT:, RECOMMENDATION:, ACTION:");

        return sb.toString();
    }
}