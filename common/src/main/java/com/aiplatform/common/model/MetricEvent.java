package com.aiplatform.common.model;

import com.aiplatform.common.enums.MetricType;
import com.aiplatform.common.enums.Severity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Core event model emitted by all services.
 * Flows through Kafka → Consumer → AI Agent → Action Executor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricEvent {

    private String eventId;
    private String serviceId;        // e.g. "payment-service", "order-service"
    private String instanceId;       // pod/instance id
    private String environment;      // prod, staging

    private MetricType metricType;   // CPU, MEMORY, LATENCY, ERROR_RATE, etc.
    private double value;            // actual metric value
    private double threshold;        // configured threshold

    private Severity severity;       // INFO, WARNING, CRITICAL

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private Map<String, String> tags; // additional metadata
    private String region;
    private String traceId;          // for distributed tracing correlation
}
