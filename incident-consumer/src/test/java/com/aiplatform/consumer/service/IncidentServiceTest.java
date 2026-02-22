package com.aiplatform.consumer.service;

import com.aiplatform.common.enums.MetricType;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.Incident;
import com.aiplatform.common.model.MetricEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentService")
class IncidentServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        incidentService = new IncidentService(redisTemplate);
    }

    @Test
    @DisplayName("creates incident when no duplicate exists")
    void createsIncidentWhenNoDuplicateExists() {
        MetricEvent event = buildEvent("payment-service", MetricType.CPU_USAGE, Severity.CRITICAL);

        // No existing dedup key
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        Incident incident = incidentService.createIncident(event, "CPU spike detected");

        assertThat(incident).isNotNull();
        assertThat(incident.getIncidentId()).isNotNull();
        assertThat(incident.getServiceId()).isEqualTo("payment-service");
        assertThat(incident.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(incident.getDescription()).isEqualTo("CPU spike detected");

        // Verify it was stored in Redis
        verify(valueOps, times(2)).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("returns null when duplicate incident exists within dedup window")
    void returnsNullWhenDuplicateExists() {
        MetricEvent event = buildEvent("payment-service", MetricType.CPU_USAGE, Severity.CRITICAL);

        // Dedup key already exists
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        Incident incident = incidentService.createIncident(event, "CPU spike detected");

        assertThat(incident).isNull();

        // Should NOT store anything in Redis
        verify(valueOps, never()).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("different service+metric combinations are not deduplicated")
    void differentServiceMetricCombinationsAreNotDeduplicated() {
        MetricEvent cpuEvent = buildEvent("payment-service", MetricType.CPU_USAGE, Severity.CRITICAL);
        MetricEvent memEvent = buildEvent("payment-service", MetricType.MEMORY_USAGE, Severity.CRITICAL);

        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        Incident incident1 = incidentService.createIncident(cpuEvent, "CPU spike");
        Incident incident2 = incidentService.createIncident(memEvent, "Memory spike");

        assertThat(incident1).isNotNull();
        assertThat(incident2).isNotNull();
        assertThat(incident1.getIncidentId()).isNotEqualTo(incident2.getIncidentId());
    }

    @Test
    @DisplayName("incident has DETECTED status on creation")
    void incidentHasDetectedStatusOnCreation() {
        MetricEvent event = buildEvent("order-service", MetricType.ERROR_RATE, Severity.WARNING);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        Incident incident = incidentService.createIncident(event, "High error rate");

        assertThat(incident.getStatus()).isEqualTo(com.aiplatform.common.enums.IncidentStatus.DETECTED);
    }

    @Test
    @DisplayName("incident detectedAt is set on creation")
    void incidentDetectedAtIsSet() {
        MetricEvent event = buildEvent("order-service", MetricType.ERROR_RATE, Severity.CRITICAL);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        Instant before = Instant.now();
        Incident incident = incidentService.createIncident(event, "Test");
        Instant after = Instant.now();

        assertThat(incident.getDetectedAt()).isAfterOrEqualTo(before);
        assertThat(incident.getDetectedAt()).isBeforeOrEqualTo(after);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MetricEvent buildEvent(String serviceId, MetricType metricType, Severity severity) {
        return MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .instanceId(serviceId + "-pod-1")
                .metricType(metricType)
                .value(90.0)
                .threshold(80.0)
                .severity(severity)
                .timestamp(Instant.now())
                .environment("production")
                .region("ap-south-1")
                .build();
    }
}