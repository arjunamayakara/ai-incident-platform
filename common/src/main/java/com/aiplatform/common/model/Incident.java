package com.aiplatform.common.model;

import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Incident created by the consumer when anomaly is detected.
 * Sent to AI Agent for root cause analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    private String incidentId;
    private String serviceId;
    private String instanceId;
    private Severity severity;
    private IncidentStatus status;

    private String description;          // human-readable description
    private List<MetricEvent> triggerEvents; // events that caused this incident

    private String aiRootCause;          // filled by AI Agent
    private String aiRecommendation;     // filled by AI Agent
    private String actionTaken;          // filled by Action Executor

    private Instant detectedAt;
    private Instant resolvedAt;
}
