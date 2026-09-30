package dev.assessment.urlshortener.engineering;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import dev.assessment.urlshortener.engineering.EngineeringModels.AgentEnvelope;
import dev.assessment.urlshortener.engineering.EngineeringModels.AgentInvocationEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.AgentRole;
import dev.assessment.urlshortener.engineering.EngineeringModels.ApprovalEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.BuildEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.BuildStatus;
import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringReliabilityMetrics;
import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringRunView;
import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringStatus;
import dev.assessment.urlshortener.engineering.EngineeringModels.HashApprovalRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.PatchEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.RequirementChangeRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.RollbackEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.StartEngineeringRunRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.ValidationEvidence;
import dev.assessment.urlshortener.engineering.analysis.RepositoryAnalyzer;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import dev.assessment.urlshortener.engineering.model.ContextSanitizer;
import dev.assessment.urlshortener.engineering.model.LlmClient.AgentPrompt;
import dev.assessment.urlshortener.engineering.model.StructuredAgentGateway;
import dev.assessment.urlshortener.engineering.patch.GovernedPatchApplier;
import dev.assessment.urlshortener.engineering.validation.BuildRunner;
import dev.assessment.urlshortener.engineering.workspace.RepositoryWorkspaceService;
import dev.assessment.urlshortener.engineering.workspace.RepositoryWorkspaceService.RollbackResult;
import dev.assessment.urlshortener.engineering.workspace.RepositoryWorkspaceService.WorkspaceEvidence;
import dev.assessment.urlshortener.governance.PolicyDecision;
import dev.assessment.urlshortener.governance.PolicyEngine;
import dev.assessment.urlshortener.governance.PolicySeverity;
import dev.assessment.urlshortener.orchestration.ScenarioType;
import dev.assessment.urlshortener.shared.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EngineeringExecutionService {
    private static final List<AgentRole> PARALLEL_CHANGE_ROLES = List.of(
            AgentRole.IMPLEMENTATION, AgentRole.TEST_GENERATION, AgentRole.DOCUMENTATION);

    private final EngineeringRunStore store;
    private final RepositoryWorkspaceService workspaces;
    private final RepositoryAnalyzer analyzer;
    private final StructuredAgentGateway agents;
    private final ContextSanitizer sanitizer;
    private final GovernedPatchApplier patches;
    private final BuildRunner builds;
    private final PolicyEngine policies;
    private final AgenticExecutionProperties properties;
    private final Clock clock;
    private final ConcurrentMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public EngineeringExecutionService(
            EngineeringRunStore store,
            RepositoryWorkspaceService workspaces,
            RepositoryAnalyzer analyzer,
            StructuredAgentGateway agents,
            ContextSanitizer sanitizer,
            GovernedPatchApplier patches,
            BuildRunner builds,
            PolicyEngine policies,
            AgenticExecutionProperties properties,
            Clock clock) {
        this.store = store;
        this.workspaces = workspaces;
        this.analyzer = analyzer;
        this.agents = agents;
        this.sanitizer = sanitizer;
        this.patches = patches;
        this.builds = builds;
        this.policies = policies;
        this.properties = properties;
        this.clock = clock;
    }

    public EngineeringRunView start(StartEngineeringRunRequest request, String actor) {
        Instant now = Instant.now(clock);
        PolicyDecision policy = policies.evaluate(request.requirement());
        EngineeringRun run = EngineeringRun.create(sanitizer.sanitize(request.requirement()), request.scenario(),
                request.repositoryPath(), request.baselineRevision(), actor, now);
        store.save(run);
        if (!policy.allowed()) {
            run.safeStop("Policy blocked execution: " + findings(policy), "policy-engine", Instant.now(clock));
            store.save(run);
            return run.view();
        }
        try {
            WorkspaceEvidence workspace = workspaces.create(run.id(), run.revision(), request.repositoryPath(),
                    run.baselineRevision());
            run.workspace(workspace.workspace().toString(), workspace.baseline().toString(),
                    workspace.baselineManifestHash(), Instant.now(clock));
            plan(run, policy);
        } catch (RuntimeException exception) {
            run.safeStop("Planning stopped safely: " + safeMessage(exception), "orchestrator", Instant.now(clock));
        }
        store.save(run);
        return run.view();
    }

    public EngineeringRunView get(UUID id) {
        return find(id).view();
    }

    public List<EngineeringRunView> list() {
        return store.findAll().stream().map(EngineeringRun::view).toList();
    }

    public EngineeringRunView approveChange(
            UUID id,
            HashApprovalRequest request,
            String actor,
            List<String> roles) {
        return locked(id, run -> {
            requireStatus(run, EngineeringStatus.AWAITING_CHANGE_APPROVAL);
            requireIndependentApprover(run, actor, roles);
            requireHash("plan", run.planHash(), request.artifactHash());
            run.changeApproved(approval("CHANGE", run.revision(), request, actor, roles), Instant.now(clock));
            store.save(run);
            executeAndValidate(run);
            store.save(run);
            return run.view();
        });
    }

    public EngineeringRunView approveRelease(
            UUID id,
            HashApprovalRequest request,
            String actor,
            List<String> roles) {
        return locked(id, run -> {
            requireStatus(run, EngineeringStatus.AWAITING_RELEASE_APPROVAL);
            requireIndependentApprover(run, actor, roles);
            requireHash("outcome", run.outcomeHash(), request.artifactHash());
            if (run.validation() == null || !run.validation().passed()) {
                throw conflict("Release approval requires a passing validation gate");
            }
            run.releaseApproved(approval("RELEASE", run.revision(), request, actor, roles), Instant.now(clock));
            store.save(run);
            return run.view();
        });
    }

    public EngineeringRunView replan(UUID id, RequirementChangeRequest request, String actor) {
        return locked(id, run -> {
            if (!List.of(EngineeringStatus.AWAITING_CLARIFICATION, EngineeringStatus.AWAITING_CHANGE_APPROVAL,
                    EngineeringStatus.SAFE_STOPPED).contains(run.status())) {
                throw conflict("Run cannot be replanned from " + run.status());
            }
            if (run.workspacePath() != null) {
                rollback(run, "Replanning upstream requirement", actor, EngineeringStatus.PLANNING);
            }
            PolicyDecision policy = policies.evaluate(request.requirement());
            run.replan(sanitizer.sanitize(request.requirement()), sanitizer.sanitize(request.reason()), actor,
                    Instant.now(clock));
            if (!policy.allowed()) {
                run.safeStop("Policy blocked revised requirement: " + findings(policy), "policy-engine", Instant.now(clock));
            } else {
                WorkspaceEvidence workspace = workspaces.create(run.id(), run.revision(), run.repositoryPath(),
                        run.baselineRevision());
                run.workspace(workspace.workspace().toString(), workspace.baseline().toString(),
                        workspace.baselineManifestHash(), Instant.now(clock));
                plan(run, policy);
            }
            store.save(run);
            return run.view();
        });
    }

    public EngineeringRunView cancel(UUID id, String reason, String actor) {
        return locked(id, run -> {
            if (terminal(run.status())) return run.view();
            if (run.workspacePath() == null) {
                run.safeStop("Cancelled: " + sanitizer.sanitize(reason), actor, Instant.now(clock));
            } else {
                rollback(run, "Cancelled: " + sanitizer.sanitize(reason), actor, EngineeringStatus.CANCELLED);
            }
            store.save(run);
            return run.view();
        });
    }

    public EngineeringReliabilityMetrics metrics() {
        List<EngineeringRunView> runs = list();
        int releaseReady = (int) runs.stream().filter(run -> run.status() == EngineeringStatus.RELEASE_READY).count();
        int stopped = (int) runs.stream().filter(run -> List.of(EngineeringStatus.SAFE_STOPPED,
                EngineeringStatus.ROLLED_BACK, EngineeringStatus.FAILED).contains(run.status())).count();
        long retries = runs.stream().mapToLong(run -> Math.max(0, run.validationAttempts().size() - 1L)).sum();
        long rollbacks = runs.stream().filter(run -> run.rollback() != null && run.rollback().attempted()).count();
        long recoveredRuns = runs.stream().filter(this::wasRecovered).count();
        long recoveryMillis = runs.stream().filter(this::wasRecovered).mapToLong(this::recoveryMillis).sum();
        long meanRecovery = recoveredRuns == 0 ? 0 : recoveryMillis / recoveredRuns;
        long meanLatency = runs.isEmpty() ? 0 : (long) runs.stream()
                .mapToLong(run -> Duration.between(run.createdAt(), run.updatedAt()).toMillis()).average().orElse(0);
        double successRate = runs.isEmpty() ? 0.0 : (double) releaseReady / runs.size();
        return new EngineeringReliabilityMetrics(runs.size(), releaseReady, stopped, successRate,
                retries, rollbacks, meanRecovery, meanLatency);
    }

    private void plan(EngineeringRun run, PolicyDecision policy) {
        var repositoryMap = analyzer.analyze(Path.of(run.workspacePath()), run.requirement());
        run.repositoryMap(repositoryMap, Instant.now(clock));
        Map<String, String> upstream = new LinkedHashMap<>();
        for (AgentRole role : planningRoles(run.scenario())) {
            AgentInvocationEvidence evidence = invoke(run, role, upstream, "");
            run.invocation(evidence, Instant.now(clock));
            upstream.put(role.name(), evidence.output().summary());
        }
        boolean clarification = run.scenario() == ScenarioType.AMBIGUOUS
                || policy.findings().stream().anyMatch(finding -> finding.severity() == PolicySeverity.WARNING);
        String planHash = EngineeringRun.sha256(run.requirement() + "|" + repositoryMap.repositoryHash()
                + "|" + upstream + "|revision=" + run.revision());
        run.planned(planHash, clarification, Instant.now(clock));
    }

    private void executeAndValidate(EngineeringRun run) {
        Path workspace = Path.of(run.workspacePath());
        Path baseline = baseline(run);
        try {
            List<AgentInvocationEvidence> changeOutputs = invokeParallel(run);
            List<EngineeringModels.FileOperation> operations = new ArrayList<>();
            for (AgentInvocationEvidence evidence : changeOutputs) {
                run.invocation(evidence, Instant.now(clock));
                operations.addAll(evidence.output().operations());
            }
            PatchEvidence patch = patches.apply(workspace, baseline, run.baselineManifestHash(), operations);
            run.patch(patch, Instant.now(clock));
            store.save(run);

            BuildEvidence latest = null;
            for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
                latest = builds.verify(workspace);
                run.buildAttempt(latest, Instant.now(clock));
                store.save(run);
                if (latest.status() == BuildStatus.PASSED && latest.testsRun() > 0 && latest.testFailures() == 0) break;
                if (attempt >= properties.maxAttempts()) {
                    rollback(run, "Validation attempts exhausted", "orchestrator", EngineeringStatus.ROLLED_BACK);
                    return;
                }
                run.repositoryMap(analyzer.analyze(workspace, run.requirement()), Instant.now(clock));
                AgentInvocationEvidence repair = invoke(run, AgentRole.REPAIR, Map.of(
                        "current-diff", run.patch().unifiedDiff()), latest.output());
                run.invocation(repair, Instant.now(clock));
                if (repair.output().operations().isEmpty()) {
                    rollback(run, "No bounded repair was available", "orchestrator", EngineeringStatus.ROLLED_BACK);
                    return;
                }
                run.patch(patches.applyRepair(workspace, baseline, run.baselineManifestHash(),
                        repair.output().operations()), Instant.now(clock));
                store.save(run);
            }

            AgentInvocationEvidence security = invoke(run, AgentRole.SECURITY_REVIEW,
                    Map.of("validated-diff", run.patch().unifiedDiff()), "Post-change security review");
            run.invocation(security, Instant.now(clock));
            AgentInvocationEvidence release = invoke(run, AgentRole.RELEASE_READINESS,
                    evidenceMap(run), "Build evidence hash=" + latest.outputHash());
            run.invocation(release, Instant.now(clock));
            ValidationEvidence validation = validation(run, latest, security, release, workspace);
            run.validated(validation, Instant.now(clock));
        } catch (RuntimeException exception) {
            rollbackAndStop(run, exception);
        }
    }

    private List<AgentInvocationEvidence> invokeParallel(EngineeringRun run) {
        Map<String, String> upstream = evidenceMap(run);
        try (var executor = Executors.newFixedThreadPool(PARALLEL_CHANGE_ROLES.size())) {
            Map<AgentRole, Future<AgentInvocationEvidence>> futures = new EnumMap<>(AgentRole.class);
            for (AgentRole role : PARALLEL_CHANGE_ROLES) {
                futures.put(role, executor.submit(() -> invoke(run, role, upstream, "")));
            }
            List<AgentInvocationEvidence> evidence = new ArrayList<>();
            long timeoutMillis = properties.modelTimeout().plusSeconds(1).toMillis();
            for (AgentRole role : PARALLEL_CHANGE_ROLES) {
                evidence.add(futures.get(role).get(timeoutMillis, TimeUnit.MILLISECONDS));
            }
            return evidence;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel agent execution was interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Parallel agent execution failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Parallel agent execution exceeded its bounded timeout", exception);
        }
    }

    private AgentInvocationEvidence invoke(
            EngineeringRun run,
            AgentRole role,
            Map<String, String> upstream,
            String failureEvidence) {
        return agents.invoke(new AgentPrompt(run.id(), run.revision(), role, run.requirement(), run.scenario(),
                run.repositoryMap(), upstream, failureEvidence));
    }

    private ValidationEvidence validation(
            EngineeringRun run,
            BuildEvidence build,
            AgentInvocationEvidence security,
            AgentInvocationEvidence release,
            Path workspace) {
        boolean patchApplied = run.patch() != null && run.patch().applied();
        boolean compiled = build.status() == BuildStatus.PASSED;
        boolean testsExecuted = build.testsRun() > 0 && !build.reportHashes().isEmpty();
        boolean testsPassed = compiled && testsExecuted && build.testFailures() == 0;
        boolean securityPassed = "COMPLETED".equalsIgnoreCase(security.responseStatus())
                && security.output().risks().stream().noneMatch(risk -> risk.toLowerCase().contains("block"));
        boolean docs = run.patch().changedFiles().stream().anyMatch(path -> path.startsWith("docs/"));
        boolean current = workspaces.manifest(workspace).equals(run.patch().changedManifestHash());
        List<String> failures = new ArrayList<>();
        if (!patchApplied) failures.add("Patch was not applied");
        if (!compiled) failures.add("Production source did not compile");
        if (!testsExecuted) failures.add("No executable tests were observed");
        if (!testsPassed) failures.add("Required tests did not pass");
        if (!securityPassed) failures.add("Security review did not complete cleanly");
        if (!docs) failures.add("Reviewer documentation was not generated");
        if (!current) failures.add("Validated workspace differs from the recorded patch manifest");
        String outcomeHash = EngineeringRun.sha256(run.planHash() + "|" + run.patch().changedManifestHash()
                + "|" + build.outputHash() + "|" + security.outputHash() + "|" + release.outputHash()
                + "|" + build.reportHashes() + "|" + run.patch().artifactHashes());
        return new ValidationEvidence(patchApplied, compiled, testsExecuted, testsPassed, securityPassed,
                docs, current, run.planHash(), outcomeHash, failures.isEmpty(), failures);
    }

    private Map<String, String> evidenceMap(EngineeringRun run) {
        Map<String, String> evidence = new LinkedHashMap<>();
        run.view().agentInvocations().forEach((role, invocations) -> {
            if (!invocations.isEmpty()) {
                AgentInvocationEvidence latest = invocations.getLast();
                evidence.put(role.name() + "-summary", latest.output().summary());
                evidence.put(role.name() + "-hash", latest.outputHash());
            }
        });
        if (run.patch() != null) evidence.put("patch-manifest", run.patch().changedManifestHash());
        return evidence;
    }

    private void rollbackAndStop(EngineeringRun run, RuntimeException exception) {
        if (run.workspacePath() == null) {
            run.safeStop(safeMessage(exception), "orchestrator", Instant.now(clock));
            return;
        }
        try {
            rollback(run, "Execution failure: " + safeMessage(exception), "orchestrator",
                    EngineeringStatus.SAFE_STOPPED);
        } catch (RuntimeException rollbackFailure) {
            run.fail("Execution and rollback failed: " + safeMessage(rollbackFailure), Instant.now(clock));
        }
    }

    private void rollback(
            EngineeringRun run,
            String reason,
            String actor,
            EngineeringStatus terminalStatus) {
        RollbackResult result = workspaces.rollback(Path.of(run.workspacePath()), baseline(run),
                run.baselineManifestHash());
        run.rolledBack(new RollbackEvidence(true, result.restored(), result.expectedManifestHash(),
                result.restoredManifestHash(), Instant.now(clock), reason), actor, terminalStatus, Instant.now(clock));
    }

    private Path baseline(EngineeringRun run) {
        return Path.of(run.workspacePath()).getParent().resolve("baseline");
    }

    private ApprovalEvidence approval(
            String gate,
            int revision,
            HashApprovalRequest request,
            String actor,
            List<String> roles) {
        return new ApprovalEvidence(gate, revision, actor, roles, Instant.now(clock),
                request.artifactHash(), sanitizer.sanitize(request.comment()));
    }

    private void requireIndependentApprover(EngineeringRun run, String actor, List<String> roles) {
        if (run.submitter().equals(actor)) throw new DomainException(HttpStatus.FORBIDDEN,
                "SELF_APPROVAL_FORBIDDEN", "The submitter cannot approve the same engineering run");
        boolean authorized = roles.stream().anyMatch(role -> role.equals("ROLE_APPROVER") || role.equals("ROLE_ADMIN"));
        if (!authorized) throw new DomainException(HttpStatus.FORBIDDEN,
                "APPROVER_ROLE_REQUIRED", "An approver or administrator role is required");
    }

    private void requireHash(String label, String expected, String supplied) {
        if (expected == null || !expected.equals(supplied)) throw new DomainException(HttpStatus.CONFLICT,
                "STALE_" + label.toUpperCase() + "_APPROVAL",
                "Approval hash does not match the current " + label + " artifact");
    }

    private void requireStatus(EngineeringRun run, EngineeringStatus expected) {
        if (run.status() != expected) throw conflict("Expected " + expected + " but run is " + run.status());
    }

    private DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, "ENGINEERING_STATE_CONFLICT", message);
    }

    private EngineeringRun find(UUID id) {
        return store.find(id).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND,
                "ENGINEERING_RUN_NOT_FOUND", "Engineering run was not found"));
    }

    private EngineeringRunView locked(UUID id, RunAction action) {
        ReentrantLock lock = locks.computeIfAbsent(id, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.apply(find(id));
        } finally {
            lock.unlock();
        }
    }

    private List<AgentRole> planningRoles(ScenarioType scenario) {
        return scenario == ScenarioType.BROWNFIELD
                ? List.of(AgentRole.REQUIREMENTS, AgentRole.IMPACT_ANALYSIS, AgentRole.ARCHITECTURE,
                        AgentRole.SECURITY_REVIEW)
                : List.of(AgentRole.REQUIREMENTS, AgentRole.ARCHITECTURE, AgentRole.SECURITY_REVIEW);
    }

    private boolean terminal(EngineeringStatus status) {
        return List.of(EngineeringStatus.RELEASE_READY, EngineeringStatus.CANCELLED,
                EngineeringStatus.ROLLED_BACK, EngineeringStatus.FAILED).contains(status);
    }

    private boolean wasRecovered(EngineeringRunView run) {
        boolean failed = false;
        for (BuildEvidence attempt : run.validationAttempts()) {
            if (attempt.status() != BuildStatus.PASSED) failed = true;
            if (failed && attempt.status() == BuildStatus.PASSED) return true;
        }
        return false;
    }

    private long recoveryMillis(EngineeringRunView run) {
        boolean failed = false;
        long elapsed = 0;
        for (BuildEvidence attempt : run.validationAttempts()) {
            if (attempt.status() != BuildStatus.PASSED) failed = true;
            if (failed) elapsed += attempt.durationMillis();
            if (failed && attempt.status() == BuildStatus.PASSED) return elapsed;
        }
        return 0;
    }

    private String findings(PolicyDecision decision) {
        return decision.findings().stream().map(finding -> finding.code() + ": " + finding.message())
                .reduce((left, right) -> left + "; " + right).orElse("No findings");
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return sanitizer.sanitize(message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message);
    }

    @FunctionalInterface
    private interface RunAction {
        EngineeringRunView apply(EngineeringRun run);
    }
}
