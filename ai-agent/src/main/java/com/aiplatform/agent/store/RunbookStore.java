package com.aiplatform.agent.store;

import com.aiplatform.agent.runbook.Runbook;
import com.aiplatform.common.enums.MetricType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Stores and retrieves runbooks from Redis.
 *
 * Key structure:
 *   runbook:{runbookId}        → full runbook object
 *   runbook:index:{serviceId}  → set of runbookIds for that service
 *   runbook:index:*            → set of runbookIds that apply to all services
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunbookStore {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PREFIX       = "runbook:";
    private static final String INDEX_PREFIX = "runbook:index:";

    public Runbook save(Runbook runbook) {
        if (runbook.getRunbookId() == null) {
            runbook.setRunbookId(UUID.randomUUID().toString());
        }
        runbook.setCreatedAt(runbook.getCreatedAt() != null ? runbook.getCreatedAt() : Instant.now());
        runbook.setUpdatedAt(Instant.now());

        redisTemplate.opsForValue().set(PREFIX + runbook.getRunbookId(), runbook);
        redisTemplate.opsForSet().add(INDEX_PREFIX + runbook.getServiceId(), runbook.getRunbookId());

        log.info("Saved runbook: id={} service={} metric={}",
                runbook.getRunbookId(), runbook.getServiceId(), runbook.getTriggerMetric());
        return runbook;
    }

    public Optional<Runbook> findById(String runbookId) {
        Object obj = redisTemplate.opsForValue().get(PREFIX + runbookId);
        return Optional.ofNullable(obj instanceof Runbook ? (Runbook) obj : null);
    }

    /**
     * Finds all runbooks relevant to a service + metric.
     * Includes: service-specific runbooks + global runbooks (serviceId="*")
     * Filtered by: triggerMetric if set, otherwise all metrics
     */
    public List<Runbook> findRelevant(String serviceId, MetricType metricType) {
        Set<Object> ids = new HashSet<>();

        Set<Object> serviceIds = redisTemplate.opsForSet().members(INDEX_PREFIX + serviceId);
        if (serviceIds != null) ids.addAll(serviceIds);

        Set<Object> globalIds = redisTemplate.opsForSet().members(INDEX_PREFIX + "*");
        if (globalIds != null) ids.addAll(globalIds);

        List<Runbook> runbooks = new ArrayList<>();
        for (Object id : ids) {
            Object obj = redisTemplate.opsForValue().get(PREFIX + id);
            if (obj instanceof Runbook rb) {
                if (rb.getTriggerMetric() == null || rb.getTriggerMetric() == metricType) {
                    runbooks.add(rb);
                }
            }
        }

        log.debug("Found {} runbooks for service={} metric={}", runbooks.size(), serviceId, metricType);
        return runbooks;
    }

    public List<Runbook> findByService(String serviceId) {
        Set<Object> ids = redisTemplate.opsForSet().members(INDEX_PREFIX + serviceId);
        if (ids == null) return List.of();

        List<Runbook> result = new ArrayList<>();
        for (Object id : ids) {
            Object obj = redisTemplate.opsForValue().get(PREFIX + id);
            if (obj instanceof Runbook rb) result.add(rb);
        }
        return result;
    }

    public void delete(String runbookId) {
        findById(runbookId).ifPresent(rb -> {
            redisTemplate.delete(PREFIX + runbookId);
            redisTemplate.opsForSet().remove(INDEX_PREFIX + rb.getServiceId(), runbookId);
            log.info("Deleted runbook: {}", runbookId);
        });
    }
}