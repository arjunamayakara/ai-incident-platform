package com.aiplatform.consumer.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Maps the exact JSON payload Prometheus Alertmanager sends to webhooks.
 *
 * Alertmanager POST body looks like:
 * {
 *   "status": "firing",
 *   "alerts": [{
 *     "status": "firing",
 *     "labels": {
 *       "alertname": "HighCpuUsage",
 *       "service": "payment-service",
 *       "severity": "critical",
 *       "instance": "payment-service-pod-1:8080"
 *     },
 *     "annotations": {
 *       "summary": "CPU usage above 80%",
 *       "description": "CPU at 94.5% for 5 minutes"
 *     },
 *     "startsAt": "2024-01-15T10:30:00Z",
 *     "generatorURL": "http://prometheus:9090/..."
 *   }]
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertmanagerPayload {

    private String status;           // "firing" or "resolved"
    private List<Alert> alerts;
    private Map<String, String> commonLabels;
    private Map<String, String> commonAnnotations;
    private String externalURL;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Alert {
        private String status;                        // "firing" or "resolved"
        private Map<String, String> labels;           // alertname, service, severity, instance
        private Map<String, String> annotations;      // summary, description, value, threshold
        private String startsAt;
        private String endsAt;
        private String generatorURL;
        private String fingerprint;
    }
}