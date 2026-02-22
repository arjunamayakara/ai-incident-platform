package com.aiplatform.agent.service;

import com.aiplatform.agent.prompt.IncidentPromptBuilder;
import com.aiplatform.common.model.Incident;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Core AI service — sends incident context to Mistral,
 * parses the structured response back into an AiDiagnosisResponse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentDiagnosisService {

    private final ChatLanguageModel chatLanguageModel;
    private final IncidentPromptBuilder promptBuilder;

    public AiDiagnosisResponse diagnose(Incident incident) {
        String prompt = promptBuilder.buildDiagnosisPrompt(incident);

        log.info("Sending incident {} to Mistral for diagnosis...", incident.getIncidentId());
        log.info("========== FULL PROMPT TO MISTRAL ==========\n{}\n============================================", prompt);

        long start = System.currentTimeMillis();

        Response<AiMessage> response = chatLanguageModel.generate(UserMessage.from(prompt));
        String rawText = response.content().text();

        long elapsed = System.currentTimeMillis() - start;
        log.info("Mistral responded in {}ms for incident {}", elapsed, incident.getIncidentId());
        log.debug("Raw LLM response:\n{}", rawText);

        return parseResponse(rawText);
    }

    /**
     * Parses the structured LLM output into fields.
     * Built to handle Mistral quirks:
     * - Preamble before the structured section
     * - Extra whitespace or markdown formatting
     * - Case variations (root_cause vs ROOT_CAUSE)
     * - Bold markers like **ROOT_CAUSE:**
     */
    private AiDiagnosisResponse parseResponse(String raw) {
        // Log raw so we can see exactly what Mistral returned
        log.info("Raw Mistral response:\n{}", raw);

        // Normalize: remove markdown bold, lowercase for matching
        String normalized = raw
                .replace("**", "")
                .replace("*", "")
                .replace("`", "");

        String rootCause     = extractField(normalized, "ROOT_CAUSE", "root_cause", "root cause");
        String impact        = extractField(normalized, "IMPACT", "impact");
        String recommendation = extractField(normalized, "RECOMMENDATION", "recommendation", "recommended action");
        String action        = extractField(normalized, "ACTION", "action", "suggested action");

        // If still not found, try to extract any meaningful sentence as root cause
        if ("Unable to determine".equals(rootCause)) {
            rootCause = extractFallbackRootCause(normalized);
        }

        action = normalizeAction(action);

        log.info("Parsed diagnosis — RootCause: {} | Action: {}", rootCause, action);

        return AiDiagnosisResponse.builder()
                .rootCause(rootCause)
                .impact(impact)
                .recommendation(recommendation)
                .action(action)
                .rawResponse(raw)
                .build();
    }

    /**
     * Tries multiple field name variants to find a match.
     * Handles: "ROOT_CAUSE:", "root_cause:", "Root Cause:", "root cause:" etc.
     */
    private String extractField(String text, String... fieldNames) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            for (String fieldName : fieldNames) {
                // Try case-insensitive match with colon
                if (trimmed.toLowerCase().startsWith(fieldName.toLowerCase() + ":")) {
                    String value = trimmed.substring(fieldName.length() + 1).trim();
                    if (!value.isEmpty()) return value;
                }
                // Also try with space before colon: "ROOT_CAUSE :"
                if (trimmed.toLowerCase().startsWith(fieldName.toLowerCase() + " :")) {
                    String value = trimmed.substring(fieldName.length() + 2).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        }
        return "Unable to determine";
    }

    /**
     * Last resort: if Mistral ignored the format entirely,
     * grab the first substantive sentence as the root cause.
     */
    private String extractFallbackRootCause(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            // Skip short lines, headers, and lines that are just field labels
            if (trimmed.length() > 30
                    && !trimmed.endsWith(":")
                    && !trimmed.startsWith("#")
                    && !trimmed.toLowerCase().startsWith("here")
                    && !trimmed.toLowerCase().startsWith("based on")) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
            }
        }
        return "Unable to determine — check raw LLM response in logs";
    }

    private String normalizeAction(String action) {
        if (action == null) return "INVESTIGATE";
        String upper = action.toUpperCase();
        if (upper.contains("RESTART")) return "RESTART_POD";
        if (upper.contains("SCALE")) return "SCALE_UP";
        if (upper.contains("ROLLBACK")) return "ROLLBACK";
        if (upper.contains("ALERT")) return "ALERT_TEAM";
        return "INVESTIGATE";
    }
}