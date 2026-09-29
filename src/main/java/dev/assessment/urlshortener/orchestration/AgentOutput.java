package dev.assessment.urlshortener.orchestration;

import java.util.List;
import java.util.Map;

public record AgentOutput(
        String summary,
        Map<String, String> artifacts,
        List<String> decisions,
        List<String> risks) {

    public AgentOutput {
        artifacts = Map.copyOf(artifacts);
        decisions = List.copyOf(decisions);
        risks = List.copyOf(risks);
    }
}

