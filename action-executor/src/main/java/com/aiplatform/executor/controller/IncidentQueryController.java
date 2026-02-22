package com.aiplatform.executor.controller;

import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.Incident;
import com.aiplatform.executor.handler.ActionResult;
import com.aiplatform.executor.handler.AlertTeamHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentQueryController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertTeamHandler alertTeamHandler;

    @GetMapping("/{incidentId}")
    public ResponseEntity<Object> getIncident(@PathVariable String incidentId) {
        Object incident = redisTemplate.opsForValue().get("incident:" + incidentId);
        if (incident == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(incident);
    }

    /**
     * Test Slack directly — bypasses the whole pipeline.
     * POST /api/v1/incidents/test-slack
     */
    @PostMapping("/test-slack")
    public ResponseEntity<Map<String, String>> testSlack() {
        Incident testIncident = Incident.builder()
                .incidentId(UUID.randomUUID().toString())
                .serviceId("payment-service")
                .instanceId("payment-service-pod-1")
                .severity(Severity.CRITICAL)
                .status(IncidentStatus.REMEDIATING)
                .description("High error rate detected — 45% of requests returning 5xx")
                .aiRootCause("Downstream database connection pool exhausted causing cascading failures")
                .aiRecommendation("Scale up DB connection pool and restart affected pods | Suggested action: ALERT_TEAM")
                .detectedAt(Instant.now())
                .build();

        ActionResult result = alertTeamHandler.execute(testIncident);

        return ResponseEntity.ok(Map.of(
                "status", result.isSuccess() ? "sent" : "failed",
                "details", result.getDetails()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("action-executor is UP");
    }
}