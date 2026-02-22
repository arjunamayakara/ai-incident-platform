package com.aiplatform.common.enums;

public enum IncidentStatus {
    DETECTED,
    ANALYZING,       // AI agent is working on it
    REMEDIATING,     // Action executor is taking action
    RESOLVED,
    ESCALATED        // Could not auto-resolve, needs human
}
