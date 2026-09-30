package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TestAgent extends AbstractAdvisoryAgent {
    @Override
    public StageType supports() {
        return StageType.TESTING;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        return output(
                task.stage().name() + " advisory test plan generated; no test result is claimed.",
                task.stage().id() + "-plan.json",
                "{\"stage\":\"" + task.stage().id() + "\",\"status\":\"planned\"}",
                List.of("Cover happy path, boundary, rejection, concurrency, and recovery behavior"),
                List.of("Use /api/v1/engineering for executable build and test-report evidence"));
    }
}

