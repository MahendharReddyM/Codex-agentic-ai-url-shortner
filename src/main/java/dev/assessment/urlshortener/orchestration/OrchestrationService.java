package dev.assessment.urlshortener.orchestration;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.assessment.urlshortener.shared.DomainException;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrchestrationService {
    private final WorkflowRunRepository repository;
    private final WorkflowGraphFactory graphFactory;
    private final AgentRegistry agentRegistry;
    private final AsyncTaskExecutor taskExecutor;
    private final Clock clock;

    public OrchestrationService(
            WorkflowRunRepository repository,
            WorkflowGraphFactory graphFactory,
            AgentRegistry agentRegistry,
            AsyncTaskExecutor applicationTaskExecutor,
            Clock clock) {
        this.repository = repository;
        this.graphFactory = graphFactory;
        this.agentRegistry = agentRegistry;
        this.taskExecutor = applicationTaskExecutor;
        this.clock = clock;
    }

    public WorkflowRunView start(StartRunRequest request) {
        WorkflowRun run = new WorkflowRun(
                request.requirement().trim(), request.scenario(), graphFactory.create(request.scenario()), now());
        repository.save(run);
        advance(run);
        return WorkflowRunView.from(run);
    }

    public WorkflowRunView get(String id) {
        return WorkflowRunView.from(find(id));
    }

    public WorkflowRunView approve(String id, ApprovalRequest request) {
        WorkflowRun run = find(id);
        try {
            run.approve(request.stageId(), request.approver(), request.comment(), now());
        } catch (IllegalStateException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "APPROVAL_NOT_EXPECTED", exception.getMessage());
        }
        advance(run);
        return WorkflowRunView.from(run);
    }

    public WorkflowRunView resume(String id) {
        WorkflowRun run = find(id);
        if (run.status() != RunStatus.RUNNING && run.status() != RunStatus.WAITING_FOR_APPROVAL) {
            throw new DomainException(HttpStatus.CONFLICT, "RUN_NOT_RESUMABLE",
                    "Only active or approval-paused runs can be resumed");
        }
        advance(run);
        return WorkflowRunView.from(run);
    }

    private void advance(WorkflowRun run) {
        run.running(now());
        boolean progressed;
        do {
            progressed = false;
            List<StageDefinition> ready = run.readyStages();
            List<StageDefinition> executable = new ArrayList<>();
            for (StageDefinition stage : ready) {
                if (stage.approvalRequired()) {
                    run.waitForApproval(stage, now());
                    progressed = true;
                } else {
                    run.startStage(stage.id(), now());
                    executable.add(stage);
                }
            }
            if (!executable.isEmpty()) {
                Map<String, AgentOutput> contextSnapshot = run.successfulOutputs();
                List<CompletableFuture<StageResult>> tasks = executable.stream()
                        .map(stage -> taskExecutor.submitCompletable(() -> execute(run, stage, contextSnapshot)))
                        .toList();
                tasks.forEach(task -> applyResult(run, task.join()));
                progressed = true;
            }
        } while (progressed && !run.hasWaitingApproval() && !run.exitsPassed());

        if (run.exitsPassed()) {
            run.succeed(now());
        } else if (!run.hasWaitingApproval() && run.readyStages().isEmpty()
                && run.executions().values().stream().anyMatch(stage -> stage.status() == StageStatus.FAILED)) {
            run.fail(now(), "No executable path remains after a stage failure");
        }
        repository.save(run);
    }

    private StageResult execute(
            WorkflowRun run,
            StageDefinition stage,
            Map<String, AgentOutput> contextSnapshot) {
        try {
            AgentTask task = new AgentTask(
                    run.id(), run.requirement(), run.scenario(), stage, contextSnapshot);
            return new StageResult(stage, agentRegistry.forStage(stage.type()).execute(task), null);
        } catch (RuntimeException exception) {
            return new StageResult(stage, null, exception.getMessage());
        }
    }

    private void applyResult(WorkflowRun run, StageResult result) {
        if (result.error() == null) {
            run.stageSucceeded(result.stage().id(), result.output(), now());
            return;
        }
        run.stageFailed(result.stage().id(), result.error(), now());
        StageExecution execution = run.executions().get(result.stage().id());
        if (execution.attempts() < result.stage().maxAttempts()) {
            run.retry(result.stage().id(), now());
        }
    }

    private WorkflowRun find(String id) {
        return repository.findById(id).orElseThrow(() ->
                new DomainException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Orchestration run was not found"));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private record StageResult(StageDefinition stage, AgentOutput output, String error) {
    }
}

