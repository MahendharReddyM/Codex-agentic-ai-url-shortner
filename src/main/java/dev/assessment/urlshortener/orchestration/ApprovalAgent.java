package dev.assessment.urlshortener.orchestration;

import org.springframework.stereotype.Component;

@Component
public class ApprovalAgent implements StageAgent {
    @Override
    public StageType supports() {
        return StageType.HUMAN_APPROVAL;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        throw new UnsupportedOperationException("Human approval stages cannot be executed by an agent");
    }
}

