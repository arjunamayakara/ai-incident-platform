package com.aiplatform.executor.handler;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActionResult {
    private boolean success;
    private String actionTaken;
    private String details;
}
