package dev.assessment.urlshortener.orchestration;

import java.util.Map;

public record AgentTask(
        String runId,
        String requirement,
        ScenarioType scenario,
        StageDefinition stage,
        Map<String, AgentOutput> priorOutputs) {

    public AgentTask {
        priorOutputs = Map.copyOf(priorOutputs);
    }
}

