package dev.assessment.urlshortener.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkflowRun {
    private final String id;
    private final String requirement;
    private final ScenarioType scenario;
    private final WorkflowGraph graph;
    private final Map<String, StageExecution> executions = new LinkedHashMap<>();
    private final List<RunEvent> events = new ArrayList<>();
    private final Instant createdAt;
    private RunStatus status = RunStatus.PLANNING;
    private Instant updatedAt;
    private Instant completedAt;
    private long version;

    public WorkflowRun(String requirement, ScenarioType scenario, WorkflowGraph graph, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.requirement = requirement;
        this.scenario = scenario;
        this.graph = graph;
        this.createdAt = now;
        this.updatedAt = now;
        graph.stages().keySet().forEach(stage -> executions.put(stage, new StageExecution()));
        event(now, "RUN_CREATED", null, "system", "Workflow run created", Map.of("scenario", scenario.name()));
    }

    public synchronized String id() { return id; }
    public synchronized String requirement() { return requirement; }
    public synchronized ScenarioType scenario() { return scenario; }
    public synchronized WorkflowGraph graph() { return graph; }
    public synchronized RunStatus status() { return status; }
    public synchronized Instant createdAt() { return createdAt; }
    public synchronized Instant updatedAt() { return updatedAt; }
    public synchronized Instant completedAt() { return completedAt; }
    public synchronized long version() { return version; }
    public synchronized Map<String, StageExecution> executions() { return Map.copyOf(executions); }
    public synchronized List<RunEvent> events() { return List.copyOf(events); }

    public synchronized Map<String, AgentOutput> successfulOutputs() {
        Map<String, AgentOutput> outputs = new LinkedHashMap<>();
        executions.forEach((id, execution) -> {
            if (execution.status() == StageStatus.SUCCEEDED && execution.output() != null) {
                outputs.put(id, execution.output());
            }
        });
        return outputs;
    }

    public synchronized List<StageDefinition> readyStages() {
        return graph.stages().values().stream()
                .filter(stage -> executions.get(stage.id()).status() == StageStatus.PENDING)
                .filter(stage -> stage.dependencies().stream()
                        .allMatch(dependency -> executions.get(dependency).status() == StageStatus.SUCCEEDED))
                .toList();
    }

    public synchronized void running(Instant now) {
        status = RunStatus.RUNNING;
        touch(now);
    }

    public synchronized void waitForApproval(StageDefinition stage, Instant now) {
        executions.get(stage.id()).waitingForApproval();
        status = RunStatus.WAITING_FOR_APPROVAL;
        event(now, "APPROVAL_REQUIRED", stage.id(), "system", stage.name(), Map.of());
    }

    public synchronized void startStage(String stageId, Instant now) {
        executions.get(stageId).start(now);
        event(now, "STAGE_STARTED", stageId, "agent", "Stage execution started", Map.of());
    }

    public synchronized void stageSucceeded(String stageId, AgentOutput output, Instant now) {
        executions.get(stageId).succeed(output, now);
        event(now, "STAGE_SUCCEEDED", stageId, "agent", output.summary(), Map.of());
    }

    public synchronized void stageFailed(String stageId, String error, Instant now) {
        executions.get(stageId).fail(error, now);
        event(now, "STAGE_FAILED", stageId, "agent", error, Map.of());
    }

    public synchronized void retry(String stageId, Instant now) {
        executions.get(stageId).resetForRetry();
        event(now, "STAGE_RETRY", stageId, "system", "Bounded retry scheduled", Map.of());
    }

    public synchronized void approve(String stageId, String actor, String comment, Instant now) {
        StageExecution execution = executions.get(stageId);
        if (execution == null || execution.status() != StageStatus.WAITING_FOR_APPROVAL) {
            throw new IllegalStateException("Stage is not waiting for approval: " + stageId);
        }
        execution.succeed(new AgentOutput(
                "Approved by " + actor,
                Map.of("approval.txt", comment == null ? "Approved" : comment),
                List.of("Human owner approved continuation"),
                List.of()), now);
        event(now, "APPROVED", stageId, actor, comment == null ? "Approved" : comment, Map.of());
    }

    public synchronized void succeed(Instant now) {
        status = RunStatus.SUCCEEDED;
        completedAt = now;
        event(now, "RUN_SUCCEEDED", null, "system", "All exit gates passed", Map.of());
    }

    public synchronized void fail(Instant now, String message) {
        status = RunStatus.FAILED;
        completedAt = now;
        event(now, "RUN_FAILED", null, "system", message, Map.of());
    }

    public synchronized boolean exitsPassed() {
        return graph.exitGates().stream()
                .allMatch(exit -> executions.get(exit).status() == StageStatus.SUCCEEDED);
    }

    public synchronized boolean hasWaitingApproval() {
        return executions.values().stream().anyMatch(stage -> stage.status() == StageStatus.WAITING_FOR_APPROVAL);
    }

    private void event(
            Instant now,
            String type,
            String stageId,
            String actor,
            String message,
            Map<String, String> details) {
        version++;
        updatedAt = now;
        events.add(new RunEvent(version, now, type, stageId, actor, message, Map.copyOf(details)));
    }

    private void touch(Instant now) {
        version++;
        updatedAt = now;
    }
}

