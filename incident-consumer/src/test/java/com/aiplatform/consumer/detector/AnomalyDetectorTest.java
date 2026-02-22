package com.aiplatform.consumer.detector;

import com.aiplatform.common.enums.MetricType;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.MetricEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnomalyDetector")
class AnomalyDetectorTest {

    private AnomalyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new AnomalyDetector();
    }

    @Nested
    @DisplayName("isAnomaly()")
    class IsAnomaly {

        @Test
        @DisplayName("CRITICAL severity is always an anomaly")
        void criticalSeverityIsAlwaysAnomaly() {
            MetricEvent event = buildEvent(MetricType.CPU_USAGE, 30.0, 80.0, Severity.CRITICAL);
            // Even though value (30) is below threshold (80), CRITICAL = anomaly
            assertThat(detector.isAnomaly(event)).isTrue();
        }

        @Test
        @DisplayName("INFO severity below threshold is not an anomaly")
        void infoSeverityBelowThresholdIsNotAnomaly() {
            MetricEvent event = buildEvent(MetricType.CPU_USAGE, 40.0, 80.0, Severity.INFO);
            assertThat(detector.isAnomaly(event)).isFalse();
        }

        @Test
        @DisplayName("value exceeding threshold is an anomaly regardless of severity")
        void thresholdBreachIsAnomaly() {
            MetricEvent event = buildEvent(MetricType.CPU_USAGE, 90.0, 80.0, Severity.WARNING);
            assertThat(detector.isAnomaly(event)).isTrue();
        }

        @Test
        @DisplayName("WARNING error rate above 2% is significant anomaly")
        void warningErrorRateAbove2PercentIsAnomaly() {
            MetricEvent event = buildEvent(MetricType.ERROR_RATE, 3.5, 5.0, Severity.WARNING);
            assertThat(detector.isAnomaly(event)).isTrue();
        }

        @Test
        @DisplayName("WARNING error rate below 2% is not significant")
        void warningErrorRateBelow2PercentIsNotAnomaly() {
            MetricEvent event = buildEvent(MetricType.ERROR_RATE, 1.0, 5.0, Severity.WARNING);
            assertThat(detector.isAnomaly(event)).isFalse();
        }

        @Test
        @DisplayName("WARNING latency above 300ms is anomaly")
        void warningLatencyAbove300msIsAnomaly() {
            MetricEvent event = buildEvent(MetricType.LATENCY_P99, 450.0, 500.0, Severity.WARNING);
            assertThat(detector.isAnomaly(event)).isTrue();
        }

        @Test
        @DisplayName("WARNING memory above 75% is anomaly — potential leak")
        void warningMemoryAbove75PercentIsAnomaly() {
            MetricEvent event = buildEvent(MetricType.MEMORY_USAGE, 78.0, 85.0, Severity.WARNING);
            assertThat(detector.isAnomaly(event)).isTrue();
        }

        @Test
        @DisplayName("WARNING memory below 75% is not anomaly")
        void warningMemoryBelow75PercentIsNotAnomaly() {
            MetricEvent event = buildEvent(MetricType.MEMORY_USAGE, 60.0, 85.0, Severity.WARNING);
            assertThat(detector.isAnomaly(event)).isFalse();
        }

        @Test
        @DisplayName("WARNING thread pool above 70% is anomaly")
        void warningThreadPoolAbove70PercentIsAnomaly() {
            MetricEvent event = buildEvent(MetricType.THREAD_POOL_EXHAUSTION, 75.0, 80.0, Severity.WARNING);
            assertThat(detector.isAnomaly(event)).isTrue();
        }
    }

    @Nested
    @DisplayName("describeAnomaly()")
    class DescribeAnomaly {

        @Test
        @DisplayName("description contains service name")
        void descriptionContainsServiceName() {
            MetricEvent event = buildEvent(MetricType.CPU_USAGE, 90.0, 80.0, Severity.CRITICAL);
            event.setServiceId("payment-service");
            String description = detector.describeAnomaly(event);
            assertThat(description).contains("payment-service");
        }

        @Test
        @DisplayName("description contains metric value and threshold")
        void descriptionContainsValueAndThreshold() {
            MetricEvent event = buildEvent(MetricType.CPU_USAGE, 90.0, 80.0, Severity.CRITICAL);
            String description = detector.describeAnomaly(event);
            assertThat(description).contains("90.00");
            assertThat(description).contains("80.00");
        }

        @Test
        @DisplayName("description contains correct unit for latency")
        void descriptionContainsCorrectUnitForLatency() {
            MetricEvent event = buildEvent(MetricType.LATENCY_P99, 1200.0, 500.0, Severity.CRITICAL);
            String description = detector.describeAnomaly(event);
            assertThat(description).contains("ms");
        }

        @Test
        @DisplayName("description contains correct unit for CPU")
        void descriptionContainsCorrectUnitForCpu() {
            MetricEvent event = buildEvent(MetricType.CPU_USAGE, 90.0, 80.0, Severity.CRITICAL);
            String description = detector.describeAnomaly(event);
            assertThat(description).contains("%");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MetricEvent buildEvent(MetricType metricType, double value,
                                   double threshold, Severity severity) {
        return MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .serviceId("test-service")
                .instanceId("test-service-pod-1")
                .environment("production")
                .metricType(metricType)
                .value(value)
                .threshold(threshold)
                .severity(severity)
                .timestamp(Instant.now())
                .region("ap-south-1")
                .build();
    }
}