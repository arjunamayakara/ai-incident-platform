package com.aiplatform.executor.handler;

import com.aiplatform.common.model.Incident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simulates pod restart via Kubernetes API.
 *
 * In production this would call:
 *   kubectl rollout restart deployment/<serviceId>
 * or use the Kubernetes Java client:
 *   appsV1Api.deleteNamespacedPod(podName, namespace, ...)
 */
@Slf4j
@Component
public class RestartPodHandler {

    public ActionResult execute(Incident incident) {
        String podName = incident.getInstanceId();
        String service = incident.getServiceId();

        log.info(" [RESTART_POD] Initiating pod restart...");
        log.info("   Service   : {}", service);
        log.info("   Pod       : {}", podName);
        log.info("   Reason    : {}", incident.getAiRootCause());

        simulateK8sCall(podName);

        log.info(" [RESTART_POD] Pod {} restarted successfully. " +
                "New pod will be scheduled by Kubernetes.", podName);

        return ActionResult.builder()
                .success(true)
                .actionTaken("RESTART_POD")
                .details(String.format(
                        "Pod '%s' of service '%s' restarted. " +
                        "Kubernetes will schedule a replacement pod automatically.",
                        podName, service))
                .build();
    }

    private void simulateK8sCall(String podName) {
        // Simulates the latency of a real Kubernetes API call
        try {
            log.info("   → Calling Kubernetes API: DELETE /api/v1/namespaces/production/pods/{}", podName);
            Thread.sleep(800);
            log.info("   → Kubernetes API responded: 200 OK");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
