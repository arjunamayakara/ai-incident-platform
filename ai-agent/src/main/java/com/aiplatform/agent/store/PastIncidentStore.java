package com.aiplatform.agent.store;

import com.aiplatform.common.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stores resolved incidents so they can be retrieved as context
 * for future AI diagnosis of the same service.
 *
 * Key structure:
 *   past:{serviceId}  → sorted set of incident summaries, scored by timestamp
 *                       (keeps only last 10 per service)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PastIncidentStore {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PREFIX    = "past:";
    private static final int    MAX_KEPT  = 5;   // keep last 5 per service
    private static final Duration TTL     = Duration.ofDays(30);

    /**
     * Save a resolved incident as historical context.
     * Called by the AI agent after diagnosis is complete.
     */
    public void save(Incident incident) {
        if (incident.getAiRootCause() == null
                || "Unable to determine".equals(incident.getAiRootCause())) {
            return; // don't save useless history
        }

        String key = PREFIX + incident.getServiceId();
        double score = incident.getDetectedAt() != null
                ? incident.getDetectedAt().toEpochMilli()
                : System.currentTimeMillis();

        // Store a compact summary — not the full incident (saves Redis memory)
        String summary = buildSummary(incident);
        redisTemplate.opsForZSet().add(key, summary, score);

        // Keep only last MAX_KEPT incidents per service
        Long size = redisTemplate.opsForZSet().size(key);
        if (size != null && size > MAX_KEPT) {
            redisTemplate.opsForZSet().removeRange(key, 0, size - MAX_KEPT - 1);
        }

        redisTemplate.expire(key, TTL);
        log.debug("Saved past incident for service={}: {}", incident.getServiceId(), summary);
    }

    /**
     * Retrieve recent past incidents for a service.
     * Returns most recent first.
     */
    public List<String> findRecent(String serviceId, int limit) {
        String key = PREFIX + serviceId;
        Set<Object> results = redisTemplate.opsForZSet()
                .reverseRange(key, 0, limit - 1);

        if (results == null) return List.of();

        List<String> summaries = new ArrayList<>();
        for (Object obj : results) {
            if (obj instanceof String s) summaries.add(s);
        }
        return summaries;
    }

    private String buildSummary(Incident incident) {
        return String.format(
                "[%s] %s | Root cause: %s | Action taken: %s | Severity: %s",
                incident.getDetectedAt() != null
                        ? incident.getDetectedAt().toString().substring(0, 10)
                        : "unknown date",
                incident.getDescription() != null
                        ? truncate(incident.getDescription(), 80)
                        : "no description",
                truncate(incident.getAiRootCause(), 100),
                incident.getActionTaken() != null
                        ? truncate(incident.getActionTaken(), 60)
                        : "unknown",
                incident.getSeverity()
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "unknown";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}