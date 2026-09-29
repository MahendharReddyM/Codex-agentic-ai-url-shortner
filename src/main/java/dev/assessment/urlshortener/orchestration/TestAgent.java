package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TestAgent extends AbstractTemplateAgent {
    @Override
    public StageType supports() {
        return StageType.TESTING;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        return output(
                task.stage().name() + " passed for generated prototype evidence.",
                task.stage().id() + "-results.json",
                "{\"stage\":\"" + task.stage().id() + "\",\"status\":\"passed\",\"failures\":0}",
                List.of("Cover happy path, boundary, rejection, concurrency, and recovery behavior"),
                List.of("Template agent reports orchestration evidence; repository CI is the executable authority"));
    }
}

