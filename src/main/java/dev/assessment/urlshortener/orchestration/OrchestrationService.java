package dev.assessment.urlshortener.orchestration;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.assessment.urlshortener.governance.PolicyDecision;
import dev.assessment.urlshortener.governance.PolicyEngine;
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
    private final PolicyEngine policyEngine;
    private final AuditTrailVerifier auditTrailVerifier;

    public OrchestrationService(
            WorkflowRunRepository repository,
            WorkflowGraphFactory graphFactory,
            AgentRegistry agentRegistry,
            AsyncTaskExecutor applicationTaskExecutor,
            PolicyEngine policyEngine,
            AuditTrailVerifier auditTrailVerifier,
            Clock clock) {
        this.repository = repository;
        this.graphFactory = graphFactory;
        this.agentRegistry = agentRegistry;
        this.taskExecutor = applicationTaskExecutor;
        this.policyEngine = policyEngine;
        this.auditTrailVerifier = auditTrailVerifier;
        this.clock = clock;
    }

    public WorkflowRunView start(StartRunRequest request) {
        WorkflowRun run = new WorkflowRun(
                request.requirement().trim(), request.scenario(), graphFactory.create(request.scenario()), now());
        repository.save(run);
        PolicyDecision policyDecision = policyEngine.evaluate(request.requirement());
        run.recordPolicy(policyDecision, now());
        if (!policyDecision.allowed()) {
            run.safeStop(now(), "Blocking policy finding requires owner remediation", "policy-engine");
            repository.save(run);
            return WorkflowRunView.from(run);
        }
        advance(run);
        return WorkflowRunView.from(run);
    }

    public WorkflowRunView get(String id) {
        return WorkflowRunView.from(find(id));
    }

    public WorkflowRunView approve(String id, ApprovalRequest request) {
        WorkflowRun run = find(id);
        try {
            if (Boolean.TRUE.equals(request.approved())) {
                run.approve(request.stageId(), request.approver(), request.comment(), now());
            } else {
                run.reject(request.stageId(), request.approver(), request.comment(), now());
                repository.save(run);
                return WorkflowRunView.from(run);
            }
        } catch (IllegalStateException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "APPROVAL_NOT_EXPECTED", exception.getMessage());
        }
        advance(run);
        return WorkflowRunView.from(run);
    }

    public WorkflowRunView replan(String id, ChangeRequest request) {
        WorkflowRun run = find(id);
        try {
            run.replan(request.sourceStageId(), request.changeSummary(), request.actor(), now());
        } catch (IllegalArgumentException exception) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_REPLAN", exception.getMessage());
        }
        advance(run);
        return WorkflowRunView.from(run);
    }

    public WorkflowRunView cancel(String id, CancelRequest request) {
        WorkflowRun run = find(id);
        try {
            run.cancel(now(), request.reason(), request.actor());
        } catch (IllegalStateException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "RUN_NOT_CANCELLABLE", exception.getMessage());
        }
        repository.save(run);
        return WorkflowRunView.from(run);
    }

    public AuditTrailView audit(String id) {
        WorkflowRun run = find(id);
        return new AuditTrailView(run.id(), auditTrailVerifier.isStructurallyValid(run.events()), run.events());
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
        } else if (run.hasWaitingApproval()) {
            run.remainPaused(now());
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
                    run.id(), run.requirement(), run.scenario(), stage, contextSnapshot, run.changeNotes());
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
        } else if (result.stage().fallbackStageId() != null) {
            AgentOutput fallback = new AgentOutput(
                    "Primary validation exhausted retries; controlled manual-validation fallback accepted.",
                    Map.of("fallback-evidence.md", "Manual validation is required before the next approval gate."),
                    List.of("Fallback cannot bypass the release approval checkpoint"),
                    List.of("Automated validation remained unavailable after bounded retries"));
            run.fallbackSucceeded(result.stage().id(), result.stage().fallbackStageId(), fallback, now());
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
