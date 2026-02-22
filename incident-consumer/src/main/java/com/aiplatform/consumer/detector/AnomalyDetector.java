package com.aiplatform.consumer.detector;

import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.MetricEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Anomaly detection engine.
 *
 * Detection strategies:
 * 1. Static threshold breach (value > threshold)
 * 2. Severity escalation (CRITICAL events always trigger)
 * 3. Extensible for ML-based detection in future iterations
 *
 * In a real system this would use time-series analysis,
 * but this rule-based approach is solid for a portfolio project.
 */
@Slf4j
@Component
public class AnomalyDetector {

    /**
     * Returns true if this event represents an anomaly that needs attention.
     */
    public boolean isAnomaly(MetricEvent event) {
        // CRITICAL severity always qualifies
        if (event.getSeverity() == Severity.CRITICAL) {
            log.debug("Anomaly detected (CRITICAL severity): service={} metric={} value={}",
                    event.getServiceId(), event.getMetricType(), event.getValue());
            return true;
        }

        // Threshold breach check
        if (event.getValue() > event.getThreshold()) {
            log.debug("Anomaly detected (threshold breach): service={} metric={} value={} threshold={}",
                    event.getServiceId(), event.getMetricType(), event.getValue(), event.getThreshold());
            return true;
        }

        // Metric-specific rules for WARNING severity
        if (event.getSeverity() == Severity.WARNING) {
            return isSignificantWarning(event);
        }

        return false;
    }

    private boolean isSignificantWarning(MetricEvent event) {
        return switch (event.getMetricType()) {
            // Error rate even at WARNING level is important
            case ERROR_RATE -> event.getValue() > 2.0;
            // Latency above 300ms warrants attention
            case LATENCY_P99 -> event.getValue() > 300.0;
            // Memory above 75% could indicate leak
            case MEMORY_USAGE -> event.getValue() > 75.0;
            // Thread pool exhaustion is serious
            case THREAD_POOL_EXHAUSTION -> event.getValue() > 70.0;
            default -> false;
        };
    }

    /**
     * Generates a human-readable description of the anomaly.
     * This is passed to the AI agent for context.
     */
    public String describeAnomaly(MetricEvent event) {
        return String.format(
                "Service '%s' (instance: %s) reported %s of %.2f%s, " +
                "exceeding threshold of %.2f%s. Severity: %s. Environment: %s. Region: %s.",
                event.getServiceId(),
                event.getInstanceId(),
                event.getMetricType().name().replace("_", " "),
                event.getValue(),
                getUnit(event),
                event.getThreshold(),
                getUnit(event),
                event.getSeverity(),
                event.getEnvironment(),
                event.getRegion()
        );
    }

    private String getUnit(MetricEvent event) {
        return switch (event.getMetricType()) {
            case CPU_USAGE, MEMORY_USAGE, ERROR_RATE, THREAD_POOL_EXHAUSTION, DB_CONNECTION_POOL -> "%";
            case LATENCY_P99, GC_PAUSE -> "ms";
            case REQUEST_RATE -> " req/s";
            case KAFKA_LAG -> " msgs";
            default -> "";
        };
    }
}
