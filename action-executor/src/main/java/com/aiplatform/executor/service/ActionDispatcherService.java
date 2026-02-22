package com.aiplatform.executor.service;

import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.model.Incident;
import com.aiplatform.executor.handler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Routes each incident to the correct action handler
 * based on the AI agent's decision.
 *
 * This is the Strategy pattern in action —
 * easy to add new handlers without touching existing code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionDispatcherService {

    private final RestartPodHandler restartPodHandler;
    private final ScaleUpHandler scaleUpHandler;
    private final RollbackHandler rollbackHandler;
    private final AlertTeamHandler alertTeamHandler;
    private final InvestigateHandler investigateHandler;
    private final RedisTemplate<String, Object> redisTemplate;

    public void dispatch(Incident incident) {
        String action = extractAction(incident.getAiRecommendation());

        log.info(" Dispatching action: {} for incident: {} (service: {})",
                action, incident.getIncidentId(), incident.getServiceId());

        ActionResult result = switch (action) {
            case "RESTART_POD"  -> restartPodHandler.execute(incident);
            case "SCALE_UP"     -> scaleUpHandler.execute(incident);
            case "ROLLBACK"     -> rollbackHandler.execute(incident);
            case "ALERT_TEAM"   -> alertTeamHandler.execute(incident);
            default             -> investigateHandler.execute(incident);
        };

        // Always notify Slack for CRITICAL incidents, even if auto-remediated
        // So the team knows what happened and what action was taken
        if (incident.getSeverity() == com.aiplatform.common.enums.Severity.CRITICAL
                && !"ALERT_TEAM".equals(action)) {
            log.info(" CRITICAL incident — notifying Slack even though auto-remediated");
            alertTeamHandler.execute(incident);
        }

        // Update incident with result and mark resolved
        incident.setActionTaken(result.getDetails());
        incident.setStatus(result.isSuccess() ? IncidentStatus.RESOLVED : IncidentStatus.ESCALATED);
        incident.setResolvedAt(Instant.now());

        // Persist final state to Redis
        redisTemplate.opsForValue().set(
                "incident:" + incident.getIncidentId(),
                incident,
                Duration.ofHours(24)
        );

        log.info(" Incident {} → status: {} | action: {}",
                incident.getIncidentId(), incident.getStatus(), result.getActionTaken());

        // Summary log — great for demo
        log.info("\n" +
                        "╔══════════════════════════════════════════════════════╗\n" +
                        "║           INCIDENT RESOLVED                          ║\n" +
                        "╠══════════════════════════════════════════════════════╣\n" +
                        "║ ID       : {}  \n" +
                        "║ Service  : {}                                        \n" +
                        "║ Severity : {}                                        \n" +
                        "║ Action   : {}                                        \n" +
                        "║ Result   : {}  \n" +
                        "╚══════════════════════════════════════════════════════╝",
                incident.getIncidentId(),
                incident.getServiceId(),
                incident.getSeverity(),
                result.getActionTaken(),
                result.getDetails());
    }

    private String extractAction(String recommendation) {
        if (recommendation == null) return "INVESTIGATE";
        String upper = recommendation.toUpperCase();
        if (upper.contains("RESTART_POD"))  return "RESTART_POD";
        if (upper.contains("SCALE_UP"))     return "SCALE_UP";
        if (upper.contains("ROLLBACK"))     return "ROLLBACK";
        if (upper.contains("ALERT_TEAM"))   return "ALERT_TEAM";
        return "INVESTIGATE";
    }
}