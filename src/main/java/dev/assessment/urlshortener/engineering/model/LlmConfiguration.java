package dev.assessment.urlshortener.engineering.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfiguration {
    @Bean
    LlmClient llmClient(
            ObjectMapper mapper,
            ContextSanitizer sanitizer,
            AgenticExecutionProperties properties) {
        if ("openai".equalsIgnoreCase(properties.provider())) {
            return new OpenAiLlmClient(mapper, sanitizer, properties);
        }
        if (!"deterministic".equalsIgnoreCase(properties.provider())) {
            throw new IllegalArgumentException("Unsupported AGENTIC_MODEL_PROVIDER: " + properties.provider());
        }
        return new DeterministicLlmClient(mapper);
    }
}
