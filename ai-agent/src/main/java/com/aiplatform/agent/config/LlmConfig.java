package com.aiplatform.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LlmConfig {

    @Value("${llm.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${llm.ollama.model:mistral}")
    private String ollamaModel;

    @Value("${llm.timeout-seconds:120}")
    private int timeoutSeconds;

    /**
     * Wires up Mistral via Ollama.
     * To switch to OpenAI later, just swap this bean for:
     *
     *   OpenAiChatModel.builder()
     *       .apiKey(apiKey)
     *       .modelName("gpt-4")
     *       .build();
     *
     * Everything else stays the same — LangChain4j abstracts the provider.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing LLM: Ollama/{} at {}", ollamaModel, ollamaBaseUrl);
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .temperature(0.3)  // lower = more deterministic, better for diagnosis
                .build();
    }
}