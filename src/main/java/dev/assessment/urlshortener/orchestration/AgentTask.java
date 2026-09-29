package dev.assessment.urlshortener.orchestration;

import java.util.Map;

public record AgentTask(
        String runId,
        String requirement,
        ScenarioType scenario,
        StageDefinition stage,
        Map<String, AgentOutput> priorOutputs,
        Map<String, String> changeNotes) {

    public AgentTask {
        priorOutputs = Map.copyOf(priorOutputs);
        changeNotes = Map.copyOf(changeNotes);
    }
}
