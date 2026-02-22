package com.aiplatform.agent.runbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Compact summary of a past resolved incident.
 * Stored per service so the AI can learn from history.
 *
 * "Last 3 times payment-service had a CPU spike,
 *  SCALE_UP resolved it in under 5 minutes."
 * — That's gold for the AI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PastIncidentSummary {
    private String incidentId;
    private String serviceId;
    private String metricType;
    private String severity;
    private String rootCause;       // what the AI diagnosed
    private String actionTaken;     // what was done
    private boolean resolved;       // did it work?
    private long resolutionMinutes; // how long it took
    private Instant occurredAt;
}