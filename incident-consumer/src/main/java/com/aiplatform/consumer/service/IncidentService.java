package com.aiplatform.consumer.service;

import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.model.Incident;
import com.aiplatform.common.model.MetricEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages incident lifecycle.
 *
 * Key design decisions:
 * - Deduplication via Redis: same service can't create 2 incidents within 2 minutes
 *   (avoids alert storms)
 * - Incidents stored in Redis for fast access by AI agent and dashboard
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String INCIDENT_KEY_PREFIX = "incident:";
    private static final String DEDUP_KEY_PREFIX = "dedup:";
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(2);
    private static final Duration INCIDENT_TTL = Duration.ofHours(24);

    /**
     * Creates a new incident from an anomalous metric event.
     * Returns null if a similar incident was created recently (deduplication).
     */
    public Incident createIncident(MetricEvent event, String description) {
        String dedupKey = DEDUP_KEY_PREFIX + event.getServiceId() + ":" + event.getMetricType();

        // Check if we already have an active incident for this service+metric
        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
            log.debug("Deduplicating incident for service={} metric={}",
                    event.getServiceId(), event.getMetricType());
            return null;
        }

        Incident incident = Incident.builder()
                .incidentId(UUID.randomUUID().toString())
                .serviceId(event.getServiceId())
                .instanceId(event.getInstanceId())
                .severity(event.getSeverity())
                .status(IncidentStatus.DETECTED)
                .description(description)
                .triggerEvents(List.of(event))
                .detectedAt(Instant.now())
                .build();

        // Store incident in Redis
        String incidentKey = INCIDENT_KEY_PREFIX + incident.getIncidentId();
        redisTemplate.opsForValue().set(incidentKey, incident, INCIDENT_TTL);

        // Set dedup lock
        redisTemplate.opsForValue().set(dedupKey, "1", DEDUP_WINDOW);

        log.info("Created incident: id={} service={} severity={} metric={}",
                incident.getIncidentId(),
                incident.getServiceId(),
                incident.getSeverity(),
                event.getMetricType());

        return incident;
    }

    public void updateIncident(Incident incident) {
        String key = INCIDENT_KEY_PREFIX + incident.getIncidentId();
        redisTemplate.opsForValue().set(key, incident, INCIDENT_TTL);
    }

    public Incident getIncident(String incidentId) {
        return (Incident) redisTemplate.opsForValue().get(INCIDENT_KEY_PREFIX + incidentId);
    }
}
