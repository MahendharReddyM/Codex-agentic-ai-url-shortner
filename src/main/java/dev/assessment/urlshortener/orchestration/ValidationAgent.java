package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ValidationAgent extends AbstractAdvisoryAgent {
    @Override
    public StageType supports() {
        return StageType.VALIDATION;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        boolean inputsPresent = task.priorOutputs().keySet().containsAll(task.stage().dependencies());
        if (!inputsPresent) {
            throw new IllegalStateException("Quality gate is missing required upstream evidence");
        }
        return output(
                "Advisory graph branches synchronized; executable validation is owned by /api/v1/engineering.",
                "quality-gate.json",
                "{\"dependenciesJoined\":true,\"executableValidation\":\"not-performed-by-legacy-api\"}",
                List.of("Release requires explicit owner approval"),
                List.of("This compatibility API cannot establish build or test success"));
    }
}

