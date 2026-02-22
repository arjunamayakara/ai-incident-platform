package com.aiplatform.producer.simulator;

import com.aiplatform.common.enums.MetricType;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.MetricEvent;
import com.aiplatform.producer.service.MetricEventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Simulates realistic microservice behavior:
 * - Normal metrics (healthy baseline)
 * - Gradual degradation (e.g., memory leak)
 * - Sudden spikes (e.g., traffic surge)
 * - Cascading failures (one service causes another to degrade)
 *
 * This is what makes the demo compelling to interviewers —
 * it's not random noise, it's realistic failure scenarios.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricSimulator {

    private final MetricEventProducerService producerService;
    private final Random random = new Random();

    // Simulated services in our fake microservice ecosystem
    private static final List<String> SERVICES = List.of(
            "payment-service",
            "order-service",
            "inventory-service",
            "notification-service",
            "api-gateway"
    );

    // Track if a service is in a "degraded" scenario
    private final Map<String, ScenarioState> serviceScenarios = new HashMap<>();
    private int tickCount = 0;

    /**
     * Emits normal metrics every 5 seconds for all services.
     */
    @Scheduled(fixedRate = 5000)
    public void emitNormalMetrics() {
        tickCount++;

        // Every 10 ticks (~50s), inject a failure scenario for demo purposes
        if (tickCount % 10 == 0) {
            injectFailureScenario();
        }

        for (String serviceId : SERVICES) {
            ScenarioState scenario = serviceScenarios.getOrDefault(serviceId, ScenarioState.HEALTHY);
            emitMetricsForService(serviceId, scenario);
        }
    }

    private void emitMetricsForService(String serviceId, ScenarioState scenario) {
        switch (scenario) {
            case HEALTHY -> emitHealthyMetrics(serviceId);
            case MEMORY_LEAK -> emitMemoryLeakMetrics(serviceId);
            case CPU_SPIKE -> emitCpuSpikeMetrics(serviceId);
            case HIGH_LATENCY -> emitHighLatencyMetrics(serviceId);
            case HIGH_ERROR_RATE -> emitHighErrorRateMetrics(serviceId);
        }
    }

    private void emitHealthyMetrics(String serviceId) {
        // CPU: 10-40%
        publishEvent(serviceId, MetricType.CPU_USAGE,
                10 + random.nextDouble() * 30, 80.0, Severity.INFO);
        // Memory: 30-50%
        publishEvent(serviceId, MetricType.MEMORY_USAGE,
                30 + random.nextDouble() * 20, 85.0, Severity.INFO);
        // Latency P99: 50-150ms
        publishEvent(serviceId, MetricType.LATENCY_P99,
                50 + random.nextDouble() * 100, 500.0, Severity.INFO);
        // Error rate: 0-0.5%
        publishEvent(serviceId, MetricType.ERROR_RATE,
                random.nextDouble() * 0.5, 5.0, Severity.INFO);
    }

    private void emitMemoryLeakMetrics(String serviceId) {
        log.warn("Simulating MEMORY LEAK scenario for {}", serviceId);
        // Memory climbing toward threshold
        double memUsage = 75 + random.nextDouble() * 20; // 75-95%
        Severity severity = memUsage > 90 ? Severity.CRITICAL : Severity.WARNING;
        publishEvent(serviceId, MetricType.MEMORY_USAGE, memUsage, 85.0, severity);
        publishEvent(serviceId, MetricType.GC_PAUSE, 800 + random.nextDouble() * 500, 200.0, Severity.WARNING);
        // CPU also elevated due to GC pressure
        publishEvent(serviceId, MetricType.CPU_USAGE, 60 + random.nextDouble() * 20, 80.0, Severity.WARNING);
        // Latency degrading as GC pauses increase
        publishEvent(serviceId, MetricType.LATENCY_P99, 600 + random.nextDouble() * 400, 500.0, Severity.WARNING);
    }

    private void emitCpuSpikeMetrics(String serviceId) {
        log.warn("Simulating CPU SPIKE scenario for {}", serviceId);
        double cpuUsage = 85 + random.nextDouble() * 14; // 85-99%
        publishEvent(serviceId, MetricType.CPU_USAGE, cpuUsage, 80.0, Severity.CRITICAL);
        publishEvent(serviceId, MetricType.LATENCY_P99, 1000 + random.nextDouble() * 2000, 500.0, Severity.CRITICAL);
        publishEvent(serviceId, MetricType.THREAD_POOL_EXHAUSTION, 95 + random.nextDouble() * 5, 80.0, Severity.CRITICAL);
    }

    private void emitHighLatencyMetrics(String serviceId) {
        log.warn("Simulating HIGH LATENCY scenario for {}", serviceId);
        publishEvent(serviceId, MetricType.LATENCY_P99, 2000 + random.nextDouble() * 3000, 500.0, Severity.CRITICAL);
        publishEvent(serviceId, MetricType.DB_CONNECTION_POOL, 90 + random.nextDouble() * 10, 80.0, Severity.CRITICAL);
        publishEvent(serviceId, MetricType.ERROR_RATE, 8 + random.nextDouble() * 5, 5.0, Severity.WARNING);
    }

    private void emitHighErrorRateMetrics(String serviceId) {
        log.warn("Simulating HIGH ERROR RATE scenario for {}", serviceId);
        publishEvent(serviceId, MetricType.ERROR_RATE, 15 + random.nextDouble() * 20, 5.0, Severity.CRITICAL);
        publishEvent(serviceId, MetricType.REQUEST_RATE, 5000 + random.nextDouble() * 3000, 1000.0, Severity.WARNING);
        publishEvent(serviceId, MetricType.LATENCY_P99, 800 + random.nextDouble() * 500, 500.0, Severity.WARNING);
    }

    /**
     * Injects a random failure scenario into a random service.
     * After 3 ticks, resets to healthy (simulating recovery or escalation to AI).
     */
    private void injectFailureScenario() {
        String targetService = SERVICES.get(random.nextInt(SERVICES.size()));
        ScenarioState[] failures = {
                ScenarioState.MEMORY_LEAK,
                ScenarioState.CPU_SPIKE,
                ScenarioState.HIGH_LATENCY,
                ScenarioState.HIGH_ERROR_RATE
        };
        ScenarioState scenario = failures[random.nextInt(failures.length)];
        log.info("Injecting {} scenario into {}", scenario, targetService);
        serviceScenarios.put(targetService, scenario);

        // Reset after 30 seconds (6 ticks)
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                log.info(" Resetting {} back to HEALTHY", targetService);
                serviceScenarios.put(targetService, ScenarioState.HEALTHY);
            }
        }, 30_000);
    }

    private void publishEvent(String serviceId, MetricType metricType,
                              double value, double threshold, Severity severity) {
        MetricEvent event = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .instanceId(serviceId + "-pod-" + (random.nextInt(3) + 1))
                .environment("production")
                .metricType(metricType)
                .value(Math.round(value * 100.0) / 100.0)
                .threshold(threshold)
                .severity(severity)
                .timestamp(Instant.now())
                .region("ap-south-1")
                .traceId(UUID.randomUUID().toString().substring(0, 16))
                .tags(Map.of("team", "platform", "version", "2.1.0"))
                .build();

        producerService.publishMetricEvent(event);
    }

    private enum ScenarioState {
        HEALTHY, MEMORY_LEAK, CPU_SPIKE, HIGH_LATENCY, HIGH_ERROR_RATE
    }
}
