package com.aiplatform.consumer.webhook;

import com.aiplatform.common.model.Incident;
import com.aiplatform.common.model.MetricEvent;
import com.aiplatform.consumer.detector.AnomalyDetector;
import com.aiplatform.consumer.publisher.IncidentPublisher;
import com.aiplatform.consumer.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Receives real webhook payloads from Prometheus Alertmanager.
 *
 * To wire this up in Alertmanager config (alertmanager.yml):
 *
 *   receivers:
 *     - name: 'ai-platform'
 *       webhook_configs:
 *         - url: 'http://incident-consumer:8082/api/v1/webhook/alertmanager'
 *           send_resolved: false
 *
 * Once connected, real production alerts flow through the same
 * AI diagnosis and auto-remediation pipeline automatically.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
public class AlertmanagerWebhookController {

    private final AlertmanagerEventConverter converter;
    private final AnomalyDetector anomalyDetector;
    private final IncidentService incidentService;
    private final IncidentPublisher incidentPublisher;

    /**
     * Alertmanager webhook endpoint.
     * POST /api/v1/webhook/alertmanager
     */
    @PostMapping("/alertmanager")
    public ResponseEntity<Map<String, Object>> receiveAlert(
            @RequestBody AlertmanagerPayload payload) {

        log.info(" Received Alertmanager webhook: status={} alerts={}",
                payload.getStatus(), payload.getAlerts() != null ? payload.getAlerts().size() : 0);

        List<MetricEvent> events = converter.convert(payload);
        List<String> createdIncidentIds = new ArrayList<>();

        for (MetricEvent event : events) {
            if (anomalyDetector.isAnomaly(event)) {
                String description = anomalyDetector.describeAnomaly(event);
                Incident incident = incidentService.createIncident(event, description);

                if (incident != null) {
                    log.warn(" Webhook → Incident created: id={} service={} severity={}",
                            incident.getIncidentId(), incident.getServiceId(), incident.getSeverity());
                    incidentPublisher.publishIncident(incident);
                    createdIncidentIds.add(incident.getIncidentId());
                } else {
                    log.debug("Deduplicated incident for service={}", event.getServiceId());
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "received",
                "eventsProcessed", events.size(),
                "incidentsCreated", createdIncidentIds.size(),
                "incidentIds", createdIncidentIds
        ));
    }

    /**
     * Generic webhook for other monitoring tools (Grafana, Datadog, etc.)
     * POST /api/v1/webhook/generic
     *
     * Accepts a pre-mapped MetricEvent directly — useful if you write
     * a thin adapter for your specific monitoring tool.
     */
    @PostMapping("/generic")
    public ResponseEntity<Map<String, Object>> receiveGenericEvent(
            @RequestBody MetricEvent event) {

        log.info(" Received generic webhook event: service={} metric={} value={}",
                event.getServiceId(), event.getMetricType(), event.getValue());

        if (anomalyDetector.isAnomaly(event)) {
            String description = anomalyDetector.describeAnomaly(event);
            Incident incident = incidentService.createIncident(event, description);

            if (incident != null) {
                incidentPublisher.publishIncident(incident);
                return ResponseEntity.ok(Map.of(
                        "status", "incident_created",
                        "incidentId", incident.getIncidentId()
                ));
            }
        }

        return ResponseEntity.ok(Map.of("status", "received_no_incident"));
    }

    /**
     * Health check — Alertmanager pings this before sending alerts.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "webhook", "ready",
                "endpoint", "/api/v1/webhook/alertmanager"
        ));
    }
}