package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ReleaseAgent extends AbstractAdvisoryAgent {
    @Override
    public StageType supports() {
        return StageType.RELEASE;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        return output(
                "Release candidate is ready; deployment remains outside the agent autonomy boundary.",
                "release-readiness.md",
                "All graph exit criteria passed. Owner may promote through the organization's normal change process.",
                List.of("The agent prepares evidence but cannot self-deploy"),
                List.of());
    }
}

