package com.aiplatform.producer.controller;

import com.aiplatform.common.enums.MetricType;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.MetricEvent;
import com.aiplatform.producer.service.MetricEventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST API to manually inject events — very useful for demos.
 * You can show an interviewer: "watch what happens when I trigger a CPU spike"
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final MetricEventProducerService producerService;

    /**
     * Trigger a specific scenario manually.
     * POST /api/v1/simulate/scenario
     * Body: { "serviceId": "payment-service", "scenario": "CPU_SPIKE" }
     */
    @PostMapping("/scenario")
    public ResponseEntity<Map<String, String>> triggerScenario(
            @RequestBody Map<String, String> request) {

        String serviceId = request.getOrDefault("serviceId", "payment-service");
        String scenario = request.getOrDefault("scenario", "CPU_SPIKE");

        MetricEvent event = buildScenarioEvent(serviceId, scenario);
        producerService.publishMetricEvent(event);

        log.info("Manually triggered scenario={} for serviceId={}", scenario, serviceId);
        return ResponseEntity.ok(Map.of(
                "status", "triggered",
                "serviceId", serviceId,
                "scenario", scenario,
                "eventId", event.getEventId()
        ));
    }

    /**
     * Publish a raw custom metric event.
     * POST /api/v1/simulate/event
     */
    @PostMapping("/event")
    public ResponseEntity<Map<String, String>> publishCustomEvent(
            @RequestBody MetricEvent event) {
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        if (event.getTimestamp() == null) event.setTimestamp(Instant.now());

        producerService.publishMetricEvent(event);
        return ResponseEntity.ok(Map.of("status", "published", "eventId", event.getEventId()));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "incident-producer"));
    }

    private MetricEvent buildScenarioEvent(String serviceId, String scenario) {
        return switch (scenario) {
            case "CPU_SPIKE" -> MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .serviceId(serviceId)
                    .instanceId(serviceId + "-pod-1")
                    .metricType(MetricType.CPU_USAGE)
                    .value(95.5)
                    .threshold(80.0)
                    .severity(Severity.CRITICAL)
                    .environment("production")
                    .region("ap-south-1")
                    .timestamp(Instant.now())
                    .tags(Map.of("triggered_by", "manual", "scenario", "CPU_SPIKE"))
                    .build();
            case "MEMORY_LEAK" -> MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .serviceId(serviceId)
                    .instanceId(serviceId + "-pod-2")
                    .metricType(MetricType.MEMORY_USAGE)
                    .value(92.3)
                    .threshold(85.0)
                    .severity(Severity.CRITICAL)
                    .environment("production")
                    .region("ap-south-1")
                    .timestamp(Instant.now())
                    .tags(Map.of("triggered_by", "manual", "scenario", "MEMORY_LEAK"))
                    .build();
            case "HIGH_ERROR_RATE" -> MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .serviceId(serviceId)
                    .instanceId(serviceId + "-pod-1")
                    .metricType(MetricType.ERROR_RATE)
                    .value(28.5)
                    .threshold(5.0)
                    .severity(Severity.CRITICAL)
                    .environment("production")
                    .region("ap-south-1")
                    .timestamp(Instant.now())
                    .tags(Map.of("triggered_by", "manual", "scenario", "HIGH_ERROR_RATE"))
                    .build();
            default -> MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .serviceId(serviceId)
                    .instanceId(serviceId + "-pod-1")
                    .metricType(MetricType.LATENCY_P99)
                    .value(4500.0)
                    .threshold(500.0)
                    .severity(Severity.CRITICAL)
                    .environment("production")
                    .region("ap-south-1")
                    .timestamp(Instant.now())
                    .tags(Map.of("triggered_by", "manual"))
                    .build();
        };
    }
}
