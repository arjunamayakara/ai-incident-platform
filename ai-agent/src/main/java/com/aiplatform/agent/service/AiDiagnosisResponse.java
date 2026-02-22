package com.aiplatform.agent.service;

import lombok.Builder;
import lombok.Data;

/**
 * Structured response parsed from the LLM output.
 */
@Data
@Builder
public class AiDiagnosisResponse {
    private String rootCause;
    private String impact;
    private String recommendation;
    private String action;        // RESTART_POD | SCALE_UP | ROLLBACK | ALERT_TEAM | INVESTIGATE
    private String rawResponse;   // kept for debugging/logging
}