package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ImplementationAgent extends AbstractTemplateAgent {
    @Override
    public StageType supports() {
        return StageType.IMPLEMENTATION;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        return output(
                "A bounded implementation change set was generated from approved design context.",
                "change-set.md",
                "Implement requirement with small domain-focused units. Inputs consumed from: "
                        + String.join(", ", task.priorOutputs().keySet()),
                List.of("No production mutation is performed by this prototype agent"),
                List.of("Generated changes require automated validation and owner review"));
    }
}

