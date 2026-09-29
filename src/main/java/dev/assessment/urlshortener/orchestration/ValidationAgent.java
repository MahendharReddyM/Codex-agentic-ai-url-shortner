package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ValidationAgent extends AbstractTemplateAgent {
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
                "Parallel branches synchronized and required evidence validated.",
                "quality-gate.json",
                "{\"tests\":\"passed\",\"documentation\":\"present\",\"policy\":\"passed\"}",
                List.of("Release requires explicit owner approval"),
                List.of("Residual risks remain documented for the approver"));
    }
}

