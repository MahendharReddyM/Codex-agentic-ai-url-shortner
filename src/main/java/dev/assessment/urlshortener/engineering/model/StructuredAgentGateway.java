package dev.assessment.urlshortener.engineering.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.EngineeringModels.AgentEnvelope;
import dev.assessment.urlshortener.engineering.EngineeringModels.AgentInvocationEvidence;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import dev.assessment.urlshortener.engineering.model.DeterministicLlmClient.ModelInvocationException;
import dev.assessment.urlshortener.engineering.model.LlmClient.AgentPrompt;
import dev.assessment.urlshortener.engineering.model.LlmClient.LlmResponse;
import org.springframework.stereotype.Service;

@Service
public class StructuredAgentGateway {
    private static final String PROMPT_VERSION = "engineering-v1";

    private final LlmClient client;
    private final ObjectMapper strictMapper;
    private final AgenticExecutionProperties properties;
    private final Clock clock;

    public StructuredAgentGateway(
            LlmClient client,
            ObjectMapper mapper,
            AgenticExecutionProperties properties,
            Clock clock) {
        this.client = client;
        this.strictMapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.properties = properties;
        this.clock = clock;
    }

    public AgentInvocationEvidence invoke(AgentPrompt prompt) {
        Instant started = Instant.now(clock);
        String inputHash = sha256(prompt.toString());
        try {
            LlmResponse response = client.generate(prompt);
            if (response.content() == null || response.content().isBlank()
                    || response.content().length() > properties.maxOutputCharacters()) {
                throw new ModelInvocationException("Model returned empty or oversized structured output");
            }
            AgentEnvelope envelope = strictMapper.readValue(response.content(), AgentEnvelope.class);
            validate(envelope);
            Instant completed = Instant.now(clock);
            return new AgentInvocationEvidence(prompt.role(), prompt.revision(), client.provider(), client.model(),
                    PROMPT_VERSION, started, completed, Math.max(0L, completed.toEpochMilli() - started.toEpochMilli()),
                    response.inputTokens(), response.outputTokens(), response.status(), inputHash,
                    sha256(response.content()), envelope);
        } catch (ModelInvocationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelInvocationException("Structured model output was invalid", exception);
        }
    }

    private void validate(AgentEnvelope envelope) {
        if (envelope.summary() == null || envelope.summary().isBlank() || envelope.summary().length() > 4_000) {
            throw new ModelInvocationException("Agent summary is missing or too large");
        }
        if (envelope.decisions().size() > 20 || envelope.risks().size() > 20
                || envelope.operations().size() > properties.maxPatchFiles()
                || envelope.artifacts().size() > 30) {
            throw new ModelInvocationException("Agent output exceeded collection limits");
        }
        long patchBytes = envelope.operations().stream()
                .mapToLong(operation -> operation.content() == null ? 0 : operation.content().getBytes(StandardCharsets.UTF_8).length)
                .sum();
        if (patchBytes > properties.maxPatchBytes()) {
            throw new ModelInvocationException("Agent patch exceeded configured byte limit");
        }
        envelope.operations().forEach(operation -> {
            if (operation.operation() == null || operation.relativePath() == null || operation.relativePath().isBlank()
                    || operation.rationale() == null || operation.rationale().isBlank()) {
                throw new ModelInvocationException("Agent returned an incomplete file operation");
            }
            if (operation.operation() != dev.assessment.urlshortener.engineering.EngineeringModels.FileOperationType.DELETE
                    && operation.content() == null) {
                throw new ModelInvocationException("Create/update operation must contain file content");
            }
        });
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
