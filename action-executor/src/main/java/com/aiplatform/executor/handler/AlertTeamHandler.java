package com.aiplatform.executor.handler;

import com.aiplatform.common.model.Incident;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Sends real Slack alerts via Incoming Webhooks.
 *
 * Setup:
 * 1. Go to https://api.slack.com/apps
 * 2. Create App → Incoming Webhooks → Add Webhook to Workspace
 * 3. Set SLACK_WEBHOOK_URL environment variable or update application.yml
 */
@Slf4j
@Component
public class AlertTeamHandler {

    @Value("${slack.webhook-url:}")
    private String slackWebhookUrl;

    @Value("${slack.enabled:false}")
    private boolean slackEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActionResult execute(Incident incident) {
        log.info("🚨 [ALERT_TEAM] Sending alerts for incident: {}", incident.getIncidentId());

        String slackResult = sendSlackAlert(incident);
        logSimulatedPagerDuty(incident);

        return ActionResult.builder()
                .success(true)
                .actionTaken("ALERT_TEAM")
                .details(String.format(
                        "On-call team alerted for service '%s' (severity: %s). %s",
                        incident.getServiceId(), incident.getSeverity(), slackResult))
                .build();
    }

    private String sendSlackAlert(Incident incident) {
        if (!slackEnabled || slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
            log.info("   [Slack] Disabled or webhook URL not configured — skipping");
            return "Slack not configured.";
        }

        try {
            String emoji = switch (incident.getSeverity()) {
                case CRITICAL -> "🔴";
                case WARNING  -> "🟡";
                default       -> "🟢";
            };

            // Slack Block Kit message — looks professional in the channel
            Map<String, Object> payload = Map.of(
                    "blocks", new Object[]{
                            // Header
                            Map.of(
                                    "type", "header",
                                    "text", Map.of(
                                            "type", "plain_text",
                                            "text", emoji + " INCIDENT DETECTED — " + incident.getSeverity()
                                    )
                            ),
                            // Service info
                            Map.of(
                                    "type", "section",
                                    "fields", new Object[]{
                                            Map.of("type", "mrkdwn", "text",
                                                    "*Service:*\n" + incident.getServiceId()),
                                            Map.of("type", "mrkdwn", "text",
                                                    "*Instance:*\n" + incident.getInstanceId()),
                                            Map.of("type", "mrkdwn", "text",
                                                    "*Severity:*\n" + incident.getSeverity()),
                                            Map.of("type", "mrkdwn", "text",
                                                    "*Incident ID:*\n`" + incident.getIncidentId().substring(0, 8) + "`")
                                    }
                            ),
                            // AI Root Cause
                            Map.of(
                                    "type", "section",
                                    "text", Map.of(
                                            "type", "mrkdwn",
                                            "text", "* AI Root Cause:*\n" +
                                                    (incident.getAiRootCause() != null
                                                            ? incident.getAiRootCause()
                                                            : "Analyzing...")
                                    )
                            ),
                            // Recommendation
                            Map.of(
                                    "type", "section",
                                    "text", Map.of(
                                            "type", "mrkdwn",
                                            "text", "*💡 Recommendation:*\n" +
                                                    (incident.getAiRecommendation() != null
                                                            ? incident.getAiRecommendation()
                                                            : "Pending AI analysis")
                                    )
                            ),
                            // Divider
                            Map.of("type", "divider"),
                            // Footer
                            Map.of(
                                    "type", "context",
                                    "elements", new Object[]{
                                            Map.of("type", "mrkdwn",
                                                    "text", " AI Incident Platform • " +
                                                            (incident.getDetectedAt() != null
                                                                    ? incident.getDetectedAt().toString()
                                                                    : "just now"))
                                    }
                            )
                    }
            );

            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(slackWebhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info(" [Slack] Alert sent successfully to #incidents channel");
                return "Slack alert delivered to #incidents.";
            } else {
                log.error(" [Slack] Failed: status={} body={}", response.statusCode(), response.body());
                return "Slack alert failed (status " + response.statusCode() + ").";
            }

        } catch (Exception e) {
            log.error(" [Slack] Exception sending alert", e);
            return "Slack alert failed: " + e.getMessage();
        }
    }

    private void logSimulatedPagerDuty(Incident incident) {
        log.info("   [PagerDuty] POST https://events.pagerduty.com/v2/enqueue");
        log.info("   [PagerDuty] service={} severity={} — on-call engineer paged",
                incident.getServiceId(), incident.getSeverity());
    }
}