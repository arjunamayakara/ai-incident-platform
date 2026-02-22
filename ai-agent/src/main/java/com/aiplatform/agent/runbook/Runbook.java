package com.aiplatform.agent.runbook;

import com.aiplatform.common.enums.MetricType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * A runbook captures your team's tribal knowledge for a specific service/scenario.
 *
 * Example:
 *   serviceId: "payment-service"
 *   triggerMetric: CPU_USAGE
 *   title: "Payment service CPU spike response"
 *   steps: [
 *     "Check if nightly batch settlement job is running — if yes, wait 10 mins",
 *     "Check DB connection pool utilization",
 *     "If connections > 80%, restart the connection pool",
 *     "If no batch job, scale up to 4 replicas immediately"
 *   ]
 *   notes: "This happens every month-end due to reconciliation jobs"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Runbook {

    private String runbookId;
    private String serviceId;          // "payment-service" or "*" for all services
    private MetricType triggerMetric;  // which metric this applies to (null = all metrics)
    private String title;
    private String description;
    private List<String> steps;        // ordered remediation steps
    private String notes;              // known issues, gotchas, context

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    private String createdBy;
}