package com.aiplatform.executor.handler;

import com.aiplatform.common.model.Incident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Creates an investigation ticket when AI isn't confident enough to auto-remediate.
 *
 * In production this would call Jira / ServiceNow API.
 */
@Slf4j
@Component
public class InvestigateHandler {

    public ActionResult execute(Incident incident) {
        String ticketId = "INC-" + incident.getIncidentId().substring(0, 8).toUpperCase();

        log.info(" [INVESTIGATE] Creating investigation ticket...");
        log.info("   Ticket ID : {}", ticketId);
        log.info("   Service   : {}", incident.getServiceId());
        log.info("   AI Note   : {}", incident.getAiRootCause());

        createJiraTicket(ticketId, incident);

        log.info(" [INVESTIGATE] Ticket {} created and assigned to platform team.", ticketId);

        return ActionResult.builder()
                .success(true)
                .actionTaken("INVESTIGATE")
                .details(String.format(
                        "Investigation ticket '%s' created for service '%s'. " +
                        "Assigned to platform-oncall team. AI notes attached.",
                        ticketId, incident.getServiceId()))
                .build();
    }

    private void createJiraTicket(String ticketId, Incident incident) {
        try {
            log.info("   → POST https://company.atlassian.net/rest/api/3/issue");
            log.info("   → Title: [{}] Incident in {} - {}", incident.getSeverity(),
                    incident.getServiceId(), incident.getDescription());
            Thread.sleep(400);
            log.info("   → Jira ticket {} created successfully", ticketId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
