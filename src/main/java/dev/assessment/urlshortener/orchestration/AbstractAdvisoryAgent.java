package dev.assessment.urlshortener.orchestration;

import java.util.List;
import java.util.Map;

/** Shared output helper for the legacy graph-visualization API. */
abstract class AbstractAdvisoryAgent implements StageAgent {
    protected AgentOutput output(
            String summary,
            String artifactName,
            String artifact,
            List<String> decisions,
            List<String> risks) {
        return new AgentOutput(summary, Map.of(artifactName, artifact), decisions, risks);
    }
}
