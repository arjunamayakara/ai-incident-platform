package com.aiplatform.agent.prompt;

import com.aiplatform.agent.runbook.Runbook;
import com.aiplatform.agent.store.PastIncidentStore;
import com.aiplatform.agent.store.RunbookStore;
import com.aiplatform.common.model.Incident;
import com.aiplatform.common.model.MetricEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds context-aware prompts for Mistral.
 *
 * The key differentiator: we inject your team's runbooks and
 * past incident history into the prompt so Mistral reasons
 * with YOUR system's context, not just generic SRE knowledge.
 *
 * This is what makes this platform different from Datadog AI:
 * Datadog doesn't know your runbooks or your incident history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentPromptBuilder {

    private final RunbookStore runbookStore;
    private final PastIncidentStore pastIncidentStore;

    public String buildDiagnosisPrompt(Incident incident) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert Site Reliability Engineer (SRE).\n");
        sb.append("Analyze this production incident and reply in EXACTLY this format.\n");
        sb.append("Do not add any introduction, explanation, or text outside these 4 lines:\n\n");
        sb.append("ROOT_CAUSE: <one sentence root cause>\n");
        sb.append("IMPACT: <one sentence business impact>\n");
        sb.append("RECOMMENDATION: <one concrete remediation step>\n");
        sb.append("ACTION: <one of: RESTART_POD | SCALE_UP | ROLLBACK | ALERT_TEAM | INVESTIGATE>\n\n");

        // ── Incident details ──────────────────────────────────────────────────
        sb.append("INCIDENT:\n");
        sb.append("Service: ").append(incident.getServiceId()).append("\n");
        sb.append("Instance: ").append(incident.getInstanceId()).append("\n");
        sb.append("Severity: ").append(incident.getSeverity()).append("\n");
        sb.append("Description: ").append(incident.getDescription()).append("\n");

        if (incident.getTriggerEvents() != null && !incident.getTriggerEvents().isEmpty()) {
            sb.append("Metrics:\n");
            for (MetricEvent event : incident.getTriggerEvents()) {
                sb.append(String.format("  - %s: %.2f (threshold: %.2f)\n",
                        event.getMetricType(), event.getValue(), event.getThreshold()));
            }
        }

        // ── Inject runbooks ───────────────────────────────────────────────────
        MetricEvent triggerEvent = incident.getTriggerEvents() != null
                && !incident.getTriggerEvents().isEmpty()
                ? incident.getTriggerEvents().get(0) : null;

        List<Runbook> runbooks = runbookStore.findRelevant(
                incident.getServiceId(),
                triggerEvent != null ? triggerEvent.getMetricType() : null
        );

        if (!runbooks.isEmpty()) {
            sb.append("\nTEAM RUNBOOKS (follow these steps — they reflect your team's knowledge):\n");
            for (Runbook rb : runbooks) {
                sb.append("Runbook: ").append(rb.getTitle()).append("\n");
                if (rb.getDescription() != null) {
                    sb.append("  Context: ").append(rb.getDescription()).append("\n");
                }
                if (rb.getSteps() != null) {
                    for (int i = 0; i < rb.getSteps().size(); i++) {
                        sb.append("  Step ").append(i + 1).append(": ")
                                .append(rb.getSteps().get(i)).append("\n");
                    }
                }
                if (rb.getNotes() != null) {
                    sb.append("  Notes: ").append(rb.getNotes()).append("\n");
                }
            }
            log.info("Injected {} runbook(s) into prompt for service={}", runbooks.size(), incident.getServiceId());
        } else {
            log.debug("No runbooks found for service={}", incident.getServiceId());
        }

        // ── Inject past incidents ─────────────────────────────────────────────
        List<String> pastIncidents = pastIncidentStore.findRecent(incident.getServiceId(), 3);

        if (!pastIncidents.isEmpty()) {
            sb.append("\nPAST INCIDENTS FOR THIS SERVICE (use for pattern recognition):\n");
            for (int i = 0; i < pastIncidents.size(); i++) {
                sb.append("  Past ").append(i + 1).append(": ").append(pastIncidents.get(i)).append("\n");
            }
            log.info("Injected {} past incident(s) into prompt for service={}", pastIncidents.size(), incident.getServiceId());
        }

        sb.append("\nRemember: reply with ONLY the 4 lines starting with ROOT_CAUSE:, IMPACT:, RECOMMENDATION:, ACTION:");

        return sb.toString();
    }
}