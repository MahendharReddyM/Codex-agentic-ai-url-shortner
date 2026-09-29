package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DocumentationAgent extends AbstractTemplateAgent {
    @Override
    public StageType supports() {
        return StageType.DOCUMENTATION;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        return output(
                "User-facing and engineering documentation updates were drafted in parallel with tests.",
                "documentation-plan.md",
                "Update setup, API contract, architecture, risks, assumptions, rollback, and walkthrough evidence.",
                List.of("Documentation is part of the quality gate, not an afterthought"),
                List.of());
    }
}

