package com.aiplatform.executor.controller;

import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for the dashboard UI.
 * Returns all incident data needed to render the dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // allow the static HTML file to call this API
public class DashboardController {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Returns all incidents for the dashboard feed.
     * GET /api/v1/dashboard/incidents
     */
    @GetMapping("/incidents")
    public ResponseEntity<Map<String, Object>> getIncidents() {
        Set<String> keys = redisTemplate.keys("incident:*");
        List<Incident> incidents = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                try {
                    Object obj = redisTemplate.opsForValue().get(key);
                    if (obj instanceof Incident incident) {
                        incidents.add(incident);
                    }
                } catch (Exception e) {
                    log.warn("Failed to deserialize incident from key: {}", key);
                }
            }
        }

        // Sort by detectedAt descending (newest first)
        incidents.sort((a, b) -> {
            if (a.getDetectedAt() == null) return 1;
            if (b.getDetectedAt() == null) return -1;
            return b.getDetectedAt().compareTo(a.getDetectedAt());
        });

        // Build summary stats
        long resolved  = incidents.stream().filter(i -> i.getStatus() == IncidentStatus.RESOLVED).count();
        long analyzing = incidents.stream().filter(i -> i.getStatus() == IncidentStatus.ANALYZING).count();
        long escalated = incidents.stream().filter(i -> i.getStatus() == IncidentStatus.ESCALATED).count();
        long detected  = incidents.stream().filter(i -> i.getStatus() == IncidentStatus.DETECTED
                || i.getStatus() == IncidentStatus.REMEDIATING).count();

        // Action breakdown
        Map<String, Long> actionCounts = incidents.stream()
                .filter(i -> i.getActionTaken() != null)
                .collect(Collectors.groupingBy(
                        i -> extractAction(i.getActionTaken()),
                        Collectors.counting()
                ));

        // Service health map
        Map<String, String> serviceHealth = new HashMap<>();
        incidents.stream()
                .collect(Collectors.groupingBy(
                        Incident::getServiceId,
                        Collectors.maxBy(Comparator.comparing(
                                i -> i.getDetectedAt() != null ? i.getDetectedAt().toEpochMilli() : 0L))
                ))
                .forEach((service, latestOpt) -> latestOpt.ifPresent(latest -> {
                    String health = switch (latest.getStatus()) {
                        case RESOLVED   -> "healthy";
                        case ESCALATED  -> "critical";
                        case ANALYZING, REMEDIATING -> "degraded";
                        default         -> "warning";
                    };
                    serviceHealth.put(service, health);
                }));

        return ResponseEntity.ok(Map.of(
                "incidents", incidents,
                "stats", Map.of(
                        "total", incidents.size(),
                        "resolved", resolved,
                        "analyzing", analyzing,
                        "escalated", escalated,
                        "active", detected
                ),
                "actionBreakdown", actionCounts,
                "serviceHealth", serviceHealth
        ));
    }

    private String extractAction(String actionTaken) {
        if (actionTaken == null) return "UNKNOWN";
        if (actionTaken.contains("SCALE_UP"))    return "SCALE_UP";
        if (actionTaken.contains("RESTART"))     return "RESTART_POD";
        if (actionTaken.contains("ROLLBACK"))    return "ROLLBACK";
        if (actionTaken.contains("ALERT"))       return "ALERT_TEAM";
        return "INVESTIGATE";
    }
}