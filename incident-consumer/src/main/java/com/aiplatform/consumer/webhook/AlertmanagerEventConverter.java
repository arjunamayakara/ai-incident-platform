package com.aiplatform.consumer.webhook;

import com.aiplatform.common.enums.MetricType;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.MetricEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts a real Prometheus Alertmanager webhook payload
 * into our internal MetricEvent model.
 *
 * This is the bridge between the real world and our platform.
 * Once converted, the event flows through the exact same
 * anomaly detection → AI agent → action executor pipeline.
 */
@Slf4j
@Component
public class AlertmanagerEventConverter {

    public List<MetricEvent> convert(AlertmanagerPayload payload) {
        List<MetricEvent> events = new ArrayList<>();

        for (AlertmanagerPayload.Alert alert : payload.getAlerts()) {
            // Only process firing alerts, not resolved ones
            if (!"firing".equalsIgnoreCase(alert.getStatus())) {
                log.debug("Skipping resolved alert: {}", alert.getLabels().get("alertname"));
                continue;
            }

            try {
                MetricEvent event = convertAlert(alert);
                events.add(event);
                log.info("Converted Alertmanager alert → MetricEvent: service={} metric={} severity={}",
                        event.getServiceId(), event.getMetricType(), event.getSeverity());
            } catch (Exception e) {
                log.error("Failed to convert alert: {}", alert.getLabels(), e);
            }
        }

        return events;
    }

    private MetricEvent convertAlert(AlertmanagerPayload.Alert alert) {
        Map<String, String> labels = alert.getLabels();
        Map<String, String> annotations = alert.getAnnotations() != null
                ? alert.getAnnotations() : Map.of();

        String serviceId = extractServiceId(labels);
        String instanceId = extractInstanceId(labels);
        MetricType metricType = mapAlertNameToMetricType(labels.get("alertname"));
        Severity severity = mapSeverity(labels.get("severity"));

        // Try to extract actual value and threshold from annotations
        // Prometheus alerts commonly include these via {{ $value }} in annotation templates
        double value = parseDoubleOrDefault(annotations.get("value"), deriveDefaultValue(severity));
        double threshold = parseDoubleOrDefault(annotations.get("threshold"), deriveDefaultThreshold(metricType));

        return MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .instanceId(instanceId)
                .environment(labels.getOrDefault("environment", "production"))
                .metricType(metricType)
                .value(value)
                .threshold(threshold)
                .severity(severity)
                .timestamp(Instant.now())
                .region(labels.getOrDefault("region", "unknown"))
                .traceId(alert.getFingerprint() != null ? alert.getFingerprint() : UUID.randomUUID().toString())
                .tags(Map.of(
                        "source", "alertmanager",
                        "alertname", labels.getOrDefault("alertname", "unknown"),
                        "summary", annotations.getOrDefault("summary", ""),
                        "description", annotations.getOrDefault("description", "")
                ))
                .build();
    }

    /**
     * Maps Prometheus alert names to our MetricType enum.
     * These are common Prometheus alert naming conventions.
     */
    private MetricType mapAlertNameToMetricType(String alertName) {
        if (alertName == null) return MetricType.CPU_USAGE;
        String upper = alertName.toUpperCase();

        if (upper.contains("CPU"))              return MetricType.CPU_USAGE;
        if (upper.contains("MEMORY") ||
                upper.contains("MEM"))              return MetricType.MEMORY_USAGE;
        if (upper.contains("LATENCY") ||
                upper.contains("RESPONSE_TIME"))    return MetricType.LATENCY_P99;
        if (upper.contains("ERROR") ||
                upper.contains("5XX"))              return MetricType.ERROR_RATE;
        if (upper.contains("REQUEST") ||
                upper.contains("RPS"))              return MetricType.REQUEST_RATE;
        if (upper.contains("GC"))               return MetricType.GC_PAUSE;
        if (upper.contains("DISK"))             return MetricType.DISK_IO;
        if (upper.contains("THREAD"))           return MetricType.THREAD_POOL_EXHAUSTION;
        if (upper.contains("DB") ||
                upper.contains("DATABASE") ||
                upper.contains("CONNECTION"))       return MetricType.DB_CONNECTION_POOL;
        if (upper.contains("KAFKA") ||
                upper.contains("LAG"))              return MetricType.KAFKA_LAG;

        return MetricType.CPU_USAGE; // safe default
    }

    private Severity mapSeverity(String severity) {
        if (severity == null) return Severity.WARNING;
        return switch (severity.toLowerCase()) {
            case "critical", "page"  -> Severity.CRITICAL;
            case "warning", "warn"   -> Severity.WARNING;
            default                  -> Severity.INFO;
        };
    }

    /**
     * Extracts service name from Prometheus labels.
     * Prometheus commonly uses 'service', 'job', or 'app' labels.
     */
    private String extractServiceId(Map<String, String> labels) {
        if (labels.containsKey("service"))   return labels.get("service");
        if (labels.containsKey("job"))       return labels.get("job");
        if (labels.containsKey("app"))       return labels.get("app");
        if (labels.containsKey("container")) return labels.get("container");
        return "unknown-service";
    }

    /**
     * Extracts pod/instance from Prometheus labels.
     * Prometheus uses 'instance', 'pod', or 'kubernetes_pod_name'.
     */
    private String extractInstanceId(Map<String, String> labels) {
        if (labels.containsKey("pod"))                   return labels.get("pod");
        if (labels.containsKey("kubernetes_pod_name"))   return labels.get("kubernetes_pod_name");
        if (labels.containsKey("instance"))              return labels.get("instance");
        return "unknown-instance";
    }

    private double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double deriveDefaultValue(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 95.0;
            case WARNING  -> 75.0;
            default       -> 50.0;
        };
    }

    private double deriveDefaultThreshold(MetricType metricType) {
        return switch (metricType) {
            case CPU_USAGE, THREAD_POOL_EXHAUSTION, DB_CONNECTION_POOL -> 80.0;
            case MEMORY_USAGE                                           -> 85.0;
            case ERROR_RATE                                             -> 5.0;
            case LATENCY_P99                                            -> 500.0;
            case GC_PAUSE                                               -> 200.0;
            case REQUEST_RATE                                           -> 1000.0;
            default                                                     -> 80.0;
        };
    }
}