package dev.assessment.urlshortener.orchestration;

public interface StageAgent {
    StageType supports();

    AgentOutput execute(AgentTask task);
}

