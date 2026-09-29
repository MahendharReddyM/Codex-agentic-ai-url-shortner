package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class RequirementAgent extends AbstractTemplateAgent {
    @Override
    public StageType supports() {
        return StageType.REQUIREMENTS;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        String acceptance = "Given the requested " + task.scenario().name().toLowerCase()
                + " change, produce an observable, tested outcome for: " + task.requirement();
        return output(
                "Requirement intent normalized and acceptance boundary identified.",
                "acceptance-criteria.md",
                acceptance,
                List.of("Treat security, rollback, tests, and documentation as acceptance criteria"),
                task.scenario() == ScenarioType.AMBIGUOUS
                        ? List.of("Scope contains ambiguity and requires explicit human confirmation")
                        : List.of());
    }
}

