package com.aiplatform.agent.service;

import com.aiplatform.agent.prompt.IncidentPromptBuilder;
import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.enums.MetricType;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.Incident;
import com.aiplatform.common.model.MetricEvent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentDiagnosisService")
class IncidentDiagnosisServiceTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Mock
    private IncidentPromptBuilder promptBuilder;

    private IncidentDiagnosisService diagnosisService;

    @BeforeEach
    void setUp() {
        diagnosisService = new IncidentDiagnosisService(chatLanguageModel, promptBuilder);
        when(promptBuilder.buildDiagnosisPrompt(any())).thenReturn("test prompt");
    }

    @Nested
    @DisplayName("Response parsing")
    class ResponseParsing {

        @Test
        @DisplayName("parses clean structured response correctly")
        void parsesCleanStructuredResponse() {
            mockLlmResponse(
                    "ROOT_CAUSE: Thread pool exhaustion due to sudden traffic spike\n" +
                            "IMPACT: Payment requests timing out affecting checkout\n" +
                            "RECOMMENDATION: Scale up payment-service to 4 replicas immediately\n" +
                            "ACTION: SCALE_UP"
            );

            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());

            assertThat(response.getRootCause()).isEqualTo("Thread pool exhaustion due to sudden traffic spike");
            assertThat(response.getImpact()).isEqualTo("Payment requests timing out affecting checkout");
            assertThat(response.getRecommendation()).isEqualTo("Scale up payment-service to 4 replicas immediately");
            assertThat(response.getAction()).isEqualTo("SCALE_UP");
        }

        @Test
        @DisplayName("handles Mistral preamble before structured response")
        void handlesPreambleBeforeStructuredResponse() {
            mockLlmResponse(
                    "Sure! Here is my analysis of the incident:\n\n" +
                            "ROOT_CAUSE: Memory leak in PDF generation library\n" +
                            "IMPACT: Order service degraded, invoices failing\n" +
                            "RECOMMENDATION: Restart the affected pod immediately\n" +
                            "ACTION: RESTART_POD"
            );

            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());

            assertThat(response.getRootCause()).isEqualTo("Memory leak in PDF generation library");
            assertThat(response.getAction()).isEqualTo("RESTART_POD");
        }

        @Test
        @DisplayName("handles bold markdown formatting from Mistral")
        void handlesBoldMarkdownFormatting() {
            mockLlmResponse(
                    "**ROOT_CAUSE:** High GC pause times due to heap pressure\n" +
                            "**IMPACT:** Increased latency across all endpoints\n" +
                            "**RECOMMENDATION:** Restart pod to clear heap\n" +
                            "**ACTION:** RESTART_POD"
            );

            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());

            assertThat(response.getRootCause()).isEqualTo("High GC pause times due to heap pressure");
            assertThat(response.getAction()).isEqualTo("RESTART_POD");
        }

        @Test
        @DisplayName("normalizes SCALE UP to SCALE_UP")
        void normalizesScaleUpVariant() {
            mockLlmResponse(
                    "ROOT_CAUSE: High traffic\n" +
                            "IMPACT: Slow responses\n" +
                            "RECOMMENDATION: Add replicas\n" +
                            "ACTION: SCALE UP"
            );

            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());
            assertThat(response.getAction()).isEqualTo("SCALE_UP");
        }

        @Test
        @DisplayName("defaults to INVESTIGATE for unknown action")
        void defaultsToInvestigateForUnknownAction() {
            mockLlmResponse(
                    "ROOT_CAUSE: Unknown issue\n" +
                            "IMPACT: Some impact\n" +
                            "RECOMMENDATION: Check logs\n" +
                            "ACTION: CHECK_LOGS"
            );

            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());
            assertThat(response.getAction()).isEqualTo("INVESTIGATE");
        }

        @Test
        @DisplayName("handles case-insensitive field names")
        void handlesCaseInsensitiveFieldNames() {
            mockLlmResponse(
                    "root_cause: DB connection pool exhausted\n" +
                            "impact: All DB queries failing\n" +
                            "recommendation: Increase pool size\n" +
                            "action: ALERT_TEAM"
            );

            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());
            assertThat(response.getRootCause()).isEqualTo("DB connection pool exhausted");
            assertThat(response.getAction()).isEqualTo("ALERT_TEAM");
        }

        @Test
        @DisplayName("raw response is always preserved for debugging")
        void rawResponseIsPreserved() {
            String rawResponse = "ROOT_CAUSE: test\nIMPACT: test\nRECOMMENDATION: test\nACTION: INVESTIGATE";
            mockLlmResponse(rawResponse);

            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());
            assertThat(response.getRawResponse()).isEqualTo(rawResponse);
        }
    }

    @Nested
    @DisplayName("Action normalization")
    class ActionNormalization {

        @Test
        @DisplayName("recognizes all valid action types")
        void recognizesAllValidActionTypes() {
            assertAction("RESTART_POD", "RESTART_POD");
            assertAction("SCALE_UP", "SCALE_UP");
            assertAction("ROLLBACK", "ROLLBACK");
            assertAction("ALERT_TEAM", "ALERT_TEAM");
            assertAction("INVESTIGATE", "INVESTIGATE");
        }

        private void assertAction(String llmAction, String expectedAction) {
            mockLlmResponse("ROOT_CAUSE: x\nIMPACT: x\nRECOMMENDATION: x\nACTION: " + llmAction);
            AiDiagnosisResponse response = diagnosisService.diagnose(buildIncident());
            assertThat(response.getAction())
                    .as("Expected action %s for LLM output %s", expectedAction, llmAction)
                    .isEqualTo(expectedAction);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockLlmResponse(String text) {
        Response<AiMessage> response = Response.from(AiMessage.from(text));
        when(chatLanguageModel.generate(any(dev.langchain4j.data.message.UserMessage.class)))
                .thenReturn(response);
    }

    private Incident buildIncident() {
        MetricEvent event = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .serviceId("payment-service")
                .instanceId("payment-service-pod-1")
                .metricType(MetricType.CPU_USAGE)
                .value(95.0)
                .threshold(80.0)
                .severity(Severity.CRITICAL)
                .timestamp(Instant.now())
                .build();

        return Incident.builder()
                .incidentId(UUID.randomUUID().toString())
                .serviceId("payment-service")
                .instanceId("payment-service-pod-1")
                .severity(Severity.CRITICAL)
                .status(IncidentStatus.DETECTED)
                .description("CPU usage at 95% exceeding threshold of 80%")
                .triggerEvents(List.of(event))
                .detectedAt(Instant.now())
                .build();
    }
}