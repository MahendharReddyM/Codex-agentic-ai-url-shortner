package dev.assessment.urlshortener.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record WorkflowRunView(
        String id,
        String requirement,
        ScenarioType scenario,
        RunStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        Set<String> entryGates,
        Set<String> exitGates,
        Map<String, StageView> stages,
        List<RunEvent> events) {

    public record StageView(
            String id,
            String name,
            StageType type,
            Set<String> dependencies,
            StageStatus status,
            int attempts,
            int maxAttempts,
            boolean approvalRequired,
            Instant startedAt,
            Instant completedAt,
            String error,
            AgentOutput output) {
    }

    static WorkflowRunView from(WorkflowRun run) {
        Map<String, StageExecution> executions = run.executions();
        Map<String, StageView> stageViews = new java.util.LinkedHashMap<>();
        run.graph().stages().forEach((id, definition) -> {
            StageExecution execution = executions.get(id);
            stageViews.put(id, new StageView(
                    id, definition.name(), definition.type(), definition.dependencies(),
                    execution.status(), execution.attempts(), definition.maxAttempts(),
                    definition.approvalRequired(), execution.startedAt(), execution.completedAt(),
                    execution.error(), execution.output()));
        });
        return new WorkflowRunView(
                run.id(), run.requirement(), run.scenario(), run.status(), run.version(),
                run.createdAt(), run.updatedAt(), run.completedAt(),
                run.graph().entryGates(), run.graph().exitGates(), stageViews, run.events());
    }
}

