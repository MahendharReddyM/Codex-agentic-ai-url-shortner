package dev.assessment.urlshortener.engineering.model;

import java.util.Map;
import java.util.UUID;

import dev.assessment.urlshortener.engineering.EngineeringModels.AgentRole;
import dev.assessment.urlshortener.engineering.EngineeringModels.RepositoryMap;
import dev.assessment.urlshortener.orchestration.ScenarioType;

public interface LlmClient {
    LlmResponse generate(AgentPrompt prompt);

    String provider();

    String model();

    record AgentPrompt(
            UUID runId,
            int revision,
            AgentRole role,
            String requirement,
            ScenarioType scenario,
            RepositoryMap repositoryMap,
            Map<String, String> upstreamArtifacts,
            String failureEvidence) {
        public AgentPrompt {
            upstreamArtifacts = upstreamArtifacts == null ? Map.of() : Map.copyOf(upstreamArtifacts);
            failureEvidence = failureEvidence == null ? "" : failureEvidence;
        }
    }

    record LlmResponse(
            String content,
            Integer inputTokens,
            Integer outputTokens,
            String status) {
    }
}
