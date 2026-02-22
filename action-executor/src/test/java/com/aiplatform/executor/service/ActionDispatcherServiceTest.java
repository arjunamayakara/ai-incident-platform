package com.aiplatform.executor.service;

import com.aiplatform.common.enums.IncidentStatus;
import com.aiplatform.common.enums.Severity;
import com.aiplatform.common.model.Incident;
import com.aiplatform.executor.handler.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionDispatcherService")
class ActionDispatcherServiceTest {

    @Mock private RestartPodHandler restartPodHandler;
    @Mock private ScaleUpHandler scaleUpHandler;
    @Mock private RollbackHandler rollbackHandler;
    @Mock private AlertTeamHandler alertTeamHandler;
    @Mock private InvestigateHandler investigateHandler;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private ActionDispatcherService dispatcher;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        dispatcher = new ActionDispatcherService(
                restartPodHandler, scaleUpHandler, rollbackHandler,
                alertTeamHandler, investigateHandler, redisTemplate
        );
    }

    @Test
    @DisplayName("routes RESTART_POD to RestartPodHandler")
    void routesRestartPodCorrectly() {
        Incident incident = buildIncident("RESTART_POD", Severity.CRITICAL);
        when(restartPodHandler.execute(any())).thenReturn(successResult("RESTART_POD"));

        dispatcher.dispatch(incident);

        verify(restartPodHandler).execute(incident);
        verify(scaleUpHandler, never()).execute(any());
        verify(rollbackHandler, never()).execute(any());
    }

    @Test
    @DisplayName("routes SCALE_UP to ScaleUpHandler")
    void routesScaleUpCorrectly() {
        Incident incident = buildIncident("SCALE_UP", Severity.CRITICAL);
        when(scaleUpHandler.execute(any())).thenReturn(successResult("SCALE_UP"));

        dispatcher.dispatch(incident);

        verify(scaleUpHandler).execute(incident);
        verify(restartPodHandler, never()).execute(any());
    }

    @Test
    @DisplayName("routes ROLLBACK to RollbackHandler")
    void routesRollbackCorrectly() {
        Incident incident = buildIncident("ROLLBACK", Severity.WARNING);
        when(rollbackHandler.execute(any())).thenReturn(successResult("ROLLBACK"));

        dispatcher.dispatch(incident);

        verify(rollbackHandler).execute(incident);
    }

    @Test
    @DisplayName("routes ALERT_TEAM to AlertTeamHandler")
    void routesAlertTeamCorrectly() {
        Incident incident = buildIncident("ALERT_TEAM", Severity.CRITICAL);
        when(alertTeamHandler.execute(any())).thenReturn(successResult("ALERT_TEAM"));

        dispatcher.dispatch(incident);

        verify(alertTeamHandler, atLeastOnce()).execute(any());
        verify(investigateHandler, never()).execute(any());
    }

    @Test
    @DisplayName("routes unknown action to InvestigateHandler")
    void routesUnknownActionToInvestigate() {
        Incident incident = buildIncident("UNKNOWN_ACTION", Severity.WARNING);
        when(investigateHandler.execute(any())).thenReturn(successResult("INVESTIGATE"));

        dispatcher.dispatch(incident);

        verify(investigateHandler).execute(incident);
    }

    @Test
    @DisplayName("CRITICAL incident always notifies Slack even after auto-remediation")
    void criticalIncidentAlwaysNotifiesSlack() {
        Incident incident = buildIncident("SCALE_UP", Severity.CRITICAL);
        when(scaleUpHandler.execute(any())).thenReturn(successResult("SCALE_UP"));
        when(alertTeamHandler.execute(any())).thenReturn(successResult("ALERT_TEAM"));

        dispatcher.dispatch(incident);

        // ScaleUp should run for the action
        verify(scaleUpHandler).execute(incident);
        // AlertTeam should also run for Slack notification
        verify(alertTeamHandler).execute(any());
    }

    @Test
    @DisplayName("WARNING incident does not force Slack notification")
    void warningIncidentDoesNotForceSlackNotification() {
        Incident incident = buildIncident("SCALE_UP", Severity.WARNING);
        when(scaleUpHandler.execute(any())).thenReturn(successResult("SCALE_UP"));

        dispatcher.dispatch(incident);

        verify(scaleUpHandler).execute(incident);
        verify(alertTeamHandler, never()).execute(any());
    }

    @Test
    @DisplayName("incident status is RESOLVED after successful action")
    void incidentStatusIsResolvedAfterSuccess() {
        Incident incident = buildIncident("RESTART_POD", Severity.CRITICAL);
        when(restartPodHandler.execute(any())).thenReturn(successResult("RESTART_POD"));
        when(alertTeamHandler.execute(any())).thenReturn(successResult("ALERT_TEAM"));

        dispatcher.dispatch(incident);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("incident is persisted to Redis after resolution")
    void incidentIsPersistedToRedis() {
        Incident incident = buildIncident("INVESTIGATE", Severity.WARNING);
        when(investigateHandler.execute(any())).thenReturn(successResult("INVESTIGATE"));

        dispatcher.dispatch(incident);

        verify(valueOps).set(anyString(), any(Incident.class), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Incident buildIncident(String action, Severity severity) {
        return Incident.builder()
                .incidentId(UUID.randomUUID().toString())
                .serviceId("payment-service")
                .instanceId("payment-service-pod-1")
                .severity(severity)
                .status(IncidentStatus.REMEDIATING)
                .description("Test incident")
                .aiRootCause("Test root cause")
                .aiRecommendation("Test recommendation | Suggested action: " + action)
                .detectedAt(Instant.now())
                .build();
    }

    private ActionResult successResult(String action) {
        return ActionResult.builder()
                .success(true)
                .actionTaken(action)
                .details("Action " + action + " completed successfully")
                .build();
    }
}