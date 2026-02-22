package com.aiplatform.executor.handler;

import com.aiplatform.common.model.Incident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simulates horizontal scaling via Kubernetes API.
 *
 * In production this would call:
 *   kubectl scale deployment/<serviceId> --replicas=<current+2>
 * or use the Kubernetes Java client to patch the deployment spec.
 */
@Slf4j
@Component
public class ScaleUpHandler {

    public ActionResult execute(Incident incident) {
        String service = incident.getServiceId();
        int currentReplicas = 2; // in real world, fetched from K8s API
        int targetReplicas = currentReplicas + 2;

        log.info("[SCALE_UP] Initiating horizontal scale-out...");
        log.info("   Service          : {}", service);
        log.info("   Current replicas : {}", currentReplicas);
        log.info("   Target replicas  : {}", targetReplicas);
        log.info("   Reason           : {}", incident.getAiRootCause());

        simulateK8sScaleCall(service, targetReplicas);

        log.info("[SCALE_UP] {} scaled from {} → {} replicas. " +
                "Load will rebalance within ~30s.", service, currentReplicas, targetReplicas);

        return ActionResult.builder()
                .success(true)
                .actionTaken("SCALE_UP")
                .details(String.format(
                        "Service '%s' scaled from %d to %d replicas. " +
                        "Traffic will rebalance as new pods become ready.",
                        service, currentReplicas, targetReplicas))
                .build();
    }

    private void simulateK8sScaleCall(String service, int replicas) {
        try {
            log.info("   → Calling Kubernetes API: PATCH /apis/apps/v1/namespaces/production/deployments/{}", service);
            log.info("   → Payload: {{spec: {{replicas: {}}}}}", replicas);
            Thread.sleep(600);
            log.info("   → Kubernetes API responded: 200 OK — deployment patched");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
