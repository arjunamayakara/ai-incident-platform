package com.aiplatform.executor.handler;

import com.aiplatform.common.model.Incident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simulates deployment rollback via Kubernetes API.
 *
 * In production:
 *   kubectl rollout undo deployment/<serviceId>
 */
@Slf4j
@Component
public class RollbackHandler {

    public ActionResult execute(Incident incident) {
        String service = incident.getServiceId();
        String previousVersion = "v2.0.9"; // in real world, fetched from deployment history

        log.info(" [ROLLBACK] Initiating deployment rollback...");
        log.info("   Service          : {}", service);
        log.info("   Rolling back to  : {}", previousVersion);
        log.info("   Reason           : {}", incident.getAiRootCause());

        simulateRollback(service, previousVersion);

        log.info(" [ROLLBACK] {} rolled back to {}. " +
                "Monitor error rates for the next 5 minutes.", service, previousVersion);

        return ActionResult.builder()
                .success(true)
                .actionTaken("ROLLBACK")
                .details(String.format(
                        "Service '%s' rolled back to version '%s'. " +
                        "Recommend monitoring error rates for 5 minutes.",
                        service, previousVersion))
                .build();
    }

    private void simulateRollback(String service, String version) {
        try {
            log.info("   → Calling Kubernetes API: POST /apis/apps/v1/namespaces/production/deployments/{}/rollback", service);
            Thread.sleep(1200);
            log.info("   → Rollback initiated. Pods cycling to version {}", version);
            Thread.sleep(500);
            log.info("   → All pods healthy on version {}", version);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
