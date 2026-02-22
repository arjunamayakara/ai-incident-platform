package com.aiplatform.agent.prompt;

import com.aiplatform.agent.runbook.Runbook;
import com.aiplatform.agent.store.PastIncidentStore;
import com.aiplatform.agent.store.RunbookStore;
import com.aiplatform.common.enums.IncidentStatus;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentPromptBuilder")
class IncidentPromptBuilderTest {

    @Mock private RunbookStore runbookStore;
    @Mock private PastIncidentStore pastIncidentStore;

    private IncidentPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new IncidentPromptBuilder(runbookStore, pastIncidentStore);
        when(runbookStore.findRelevant(anyString(), any())).thenReturn(List.of());
        when(pastIncidentStore.findRecent(anyString(), any(int.class))).thenReturn(List.of());
    }

    @Test
    @DisplayName("prompt contains service name")
    void promptContainsServiceName() {
        Incident incident = buildIncident("payment-service");
        String prompt = promptBuilder.buildDiagnosisPrompt(incident);
        assertThat(prompt).contains("payment-service");
    }

    @Test
    @DisplayName("prompt contains all 4 required output fields")
    void promptContainsAllRequiredOutputFields() {
        String prompt = promptBuilder.buildDiagnosisPrompt(buildIncident("test-service"));
        assertThat(prompt).contains("ROOT_CAUSE:");
        assertThat(prompt).contains("IMPACT:");
        assertThat(prompt).contains("RECOMMENDATION:");
        assertThat(prompt).contains("ACTION:");
    }

    @Test
    @DisplayName("prompt contains metric value and threshold")
    void promptContainsMetricDetails() {
        Incident incident = buildIncident("payment-service");
        String prompt = promptBuilder.buildDiagnosisPrompt(incident);
        assertThat(prompt).contains("95.00");
        assertThat(prompt).contains("80.00");
        assertThat(prompt).contains("CPU_USAGE");
    }

    @Test
    @DisplayName("prompt injects runbook steps when runbook exists")
    void promptInjectsRunbookWhenExists() {
        Runbook runbook = Runbook.builder()
                .runbookId(UUID.randomUUID().toString())
                .serviceId("payment-service")
                .title("CPU spike response")
                .steps(List.of(
                        "Check if batch job is running",
                        "Scale up if no batch job"
                ))
                .notes("Month-end jobs cause spikes")
                .build();

        when(runbookStore.findRelevant(anyString(), any())).thenReturn(List.of(runbook));

        String prompt = promptBuilder.buildDiagnosisPrompt(buildIncident("payment-service"));

        assertThat(prompt).contains("TEAM RUNBOOKS");
        assertThat(prompt).contains("CPU spike response");
        assertThat(prompt).contains("Check if batch job is running");
        assertThat(prompt).contains("Scale up if no batch job");
        assertThat(prompt).contains("Month-end jobs cause spikes");
    }

    @Test
    @DisplayName("prompt does not contain runbook section when no runbooks exist")
    void promptDoesNotContainRunbookSectionWhenEmpty() {
        when(runbookStore.findRelevant(anyString(), any())).thenReturn(List.of());

        String prompt = promptBuilder.buildDiagnosisPrompt(buildIncident("payment-service"));

        assertThat(prompt).doesNotContain("TEAM RUNBOOKS");
    }

    @Test
    @DisplayName("prompt injects past incidents when history exists")
    void promptInjectsPastIncidentsWhenExists() {
        List<String> history = List.of(
                "[2026-01-15] CPU spike caused by batch job | Root cause: Settlement job | Action: waited 10 mins",
                "[2026-01-20] CPU spike | Root cause: Traffic surge | Action: SCALE_UP"
        );
        when(pastIncidentStore.findRecent(anyString(), any(int.class))).thenReturn(history);

        String prompt = promptBuilder.buildDiagnosisPrompt(buildIncident("payment-service"));

        assertThat(prompt).contains("PAST INCIDENTS FOR THIS SERVICE");
        assertThat(prompt).contains("Settlement job");
        assertThat(prompt).contains("Traffic surge");
    }

    @Test
    @DisplayName("prompt does not contain past incidents section when no history")
    void promptDoesNotContainPastIncidentsSectionWhenEmpty() {
        when(pastIncidentStore.findRecent(anyString(), any(int.class))).thenReturn(List.of());

        String prompt = promptBuilder.buildDiagnosisPrompt(buildIncident("payment-service"));

        assertThat(prompt).doesNotContain("PAST INCIDENTS FOR THIS SERVICE");
    }

    @Test
    @DisplayName("prompt ends with reminder to use exact format")
    void promptEndsWithFormatReminder() {
        String prompt = promptBuilder.buildDiagnosisPrompt(buildIncident("test-service"));
        assertThat(prompt).contains("Remember: reply with ONLY the 4 lines");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Incident buildIncident(String serviceId) {
        MetricEvent event = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .instanceId(serviceId + "-pod-1")
                .metricType(MetricType.CPU_USAGE)
                .value(95.0)
                .threshold(80.0)
                .severity(Severity.CRITICAL)
                .timestamp(Instant.now())
                .build();

        return Incident.builder()
                .incidentId(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .instanceId(serviceId + "-pod-1")
                .severity(Severity.CRITICAL)
                .status(IncidentStatus.DETECTED)
                .description("CPU at 95% exceeding threshold of 80%")
                .triggerEvents(List.of(event))
                .detectedAt(Instant.now())
                .build();
    }
}