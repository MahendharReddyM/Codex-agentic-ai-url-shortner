package dev.assessment.urlshortener.engineering;

import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import dev.assessment.urlshortener.engineering.EngineeringModels.AgentInvocationEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.AgentRole;
import dev.assessment.urlshortener.engineering.EngineeringModels.ApprovalEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.AuditEvent;
import dev.assessment.urlshortener.engineering.EngineeringModels.BuildEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringRunView;
import dev.assessment.urlshortener.engineering.EngineeringModels.PatchEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.RepositoryMap;
import dev.assessment.urlshortener.engineering.EngineeringModels.RollbackEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.ValidationEvidence;
import dev.assessment.urlshortener.orchestration.ScenarioType;

public final class EngineeringRun {
    private UUID id;
    private int revision;
    private EngineeringStatus status;
    private String requirement;
    private ScenarioType scenario;
    private String repositoryPath;
    private String baselineRevision;
    private String submitter;
    private String workspacePath;
    private String baselinePath;
    private String baselineManifestHash;
    private RepositoryMap repositoryMap;
    private String planHash;
    private String outcomeHash;
    private Map<AgentRole, List<AgentInvocationEvidence>> agentInvocations = new EnumMap<>(AgentRole.class);
    private PatchEvidence patch;
    private List<BuildEvidence> validationAttempts = new ArrayList<>();
    private ValidationEvidence validation;
    private List<ApprovalEvidence> approvals = new ArrayList<>();
    private RollbackEvidence rollback;
    private List<AuditEvent> auditTrail = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    private EngineeringRun() {
        // Jackson persistence constructor.
    }

    public static EngineeringRun create(
            String requirement,
            ScenarioType scenario,
            String repositoryPath,
            String baselineRevision,
            String submitter,
            Instant now) {
        EngineeringRun run = new EngineeringRun();
        run.id = UUID.randomUUID();
        run.revision = 1;
        run.status = EngineeringStatus.PLANNING;
        run.requirement = requirement;
        run.scenario = scenario;
        run.repositoryPath = repositoryPath;
        run.baselineRevision = baselineRevision == null || baselineRevision.isBlank() ? "WORKING_TREE" : baselineRevision;
        run.submitter = submitter;
        run.createdAt = now;
        run.updatedAt = now;
        run.audit(now, "RUN_CREATED", submitter, "Engineering run created");
        return run;
    }

    public UUID id() { return id; }
    public int revision() { return revision; }
    public EngineeringStatus status() { return status; }
    public String requirement() { return requirement; }
    public ScenarioType scenario() { return scenario; }
    public String repositoryPath() { return repositoryPath; }
    public String baselineRevision() { return baselineRevision; }
    public String submitter() { return submitter; }
    public String workspacePath() { return workspacePath; }
    public String baselinePath() { return baselinePath; }
    public String baselineManifestHash() { return baselineManifestHash; }
    public RepositoryMap repositoryMap() { return repositoryMap; }
    public String planHash() { return planHash; }
    public String outcomeHash() { return outcomeHash; }
    public PatchEvidence patch() { return patch; }
    public List<BuildEvidence> validationAttempts() { return List.copyOf(validationAttempts); }
    public ValidationEvidence validation() { return validation; }
    public RollbackEvidence rollback() { return rollback; }

    public void workspace(String workspacePath, String baselinePath, String baselineManifestHash, Instant now) {
        this.workspacePath = workspacePath;
        this.baselinePath = baselinePath;
        this.baselineManifestHash = baselineManifestHash;
        audit(now, "WORKSPACE_CREATED", "workspace-service", "Isolated workspace and baseline created");
    }

    public void repositoryMap(RepositoryMap repositoryMap, Instant now) {
        this.repositoryMap = repositoryMap;
        audit(now, "REPOSITORY_ANALYZED", "repository-analyzer",
                "Repository map hash=" + repositoryMap.repositoryHash());
    }

    public void invocation(AgentInvocationEvidence evidence, Instant now) {
        agentInvocations.computeIfAbsent(evidence.role(), ignored -> new ArrayList<>()).add(evidence);
        audit(now, "AGENT_COMPLETED", "model-gateway",
                evidence.role() + " output=" + evidence.outputHash() + " status=" + evidence.responseStatus());
    }

    public void planned(String planHash, boolean clarificationRequired, Instant now) {
        this.planHash = planHash;
        this.status = clarificationRequired
                ? EngineeringStatus.AWAITING_CLARIFICATION
                : EngineeringStatus.AWAITING_CHANGE_APPROVAL;
        audit(now, clarificationRequired ? "CLARIFICATION_REQUIRED" : "PLAN_READY", "orchestrator",
                "Plan hash=" + planHash);
    }

    public void changeApproved(ApprovalEvidence approval, Instant now) {
        approvals.add(approval);
        status = EngineeringStatus.EXECUTING;
        audit(now, "CHANGE_APPROVED", approval.principal(), "Approved plan hash=" + approval.artifactHash());
    }

    public void patch(PatchEvidence patch, Instant now) {
        this.patch = patch;
        status = EngineeringStatus.VALIDATING;
        audit(now, "PATCH_APPLIED", "patch-engine",
                "Proposal=" + patch.proposalHash() + ", changedFiles=" + patch.changedFiles().size());
    }

    public void buildAttempt(BuildEvidence evidence, Instant now) {
        validationAttempts.add(evidence);
        audit(now, "BUILD_" + evidence.status(), "build-executor",
                evidence.capability() + " exit=" + evidence.exitCode() + " tests=" + evidence.testsRun());
    }

    public void validated(ValidationEvidence evidence, Instant now) {
        validation = evidence;
        outcomeHash = evidence.outcomeHash();
        status = evidence.passed()
                ? EngineeringStatus.AWAITING_RELEASE_APPROVAL
                : EngineeringStatus.SAFE_STOPPED;
        audit(now, evidence.passed() ? "QUALITY_GATE_PASSED" : "QUALITY_GATE_FAILED", "quality-gate",
                evidence.passed() ? "Outcome hash=" + outcomeHash : String.join("; ", evidence.failures()));
    }

    public void releaseApproved(ApprovalEvidence approval, Instant now) {
        approvals.add(approval);
        status = EngineeringStatus.RELEASE_READY;
        audit(now, "RELEASE_APPROVED", approval.principal(), "Approved outcome hash=" + approval.artifactHash());
    }

    public void replan(String changedRequirement, String reason, String actor, Instant now) {
        revision++;
        requirement = changedRequirement;
        status = EngineeringStatus.PLANNING;
        planHash = null;
        outcomeHash = null;
        patch = null;
        validationAttempts = new ArrayList<>();
        validation = null;
        rollback = null;
        audit(now, "RUN_REPLANNED", actor, "Revision " + revision + ": " + reason);
    }

    public void rolledBack(RollbackEvidence evidence, String actor, EngineeringStatus terminalStatus, Instant now) {
        rollback = evidence;
        status = terminalStatus;
        audit(now, "WORKSPACE_ROLLED_BACK", actor,
                "restored=" + evidence.restored() + ", manifest=" + evidence.restoredManifestHash());
    }

    public void fail(String details, Instant now) {
        status = EngineeringStatus.FAILED;
        audit(now, "RUN_FAILED", "orchestrator", details);
    }

    public void safeStop(String details, String actor, Instant now) {
        status = EngineeringStatus.SAFE_STOPPED;
        audit(now, "SAFE_STOP", actor, details);
    }

    public EngineeringRunView view() {
        Map<AgentRole, List<AgentInvocationEvidence>> invocationCopy = new EnumMap<>(AgentRole.class);
        agentInvocations.forEach((role, evidence) -> invocationCopy.put(role, List.copyOf(evidence)));
        return new EngineeringRunView(
                id, revision, status, requirement, scenario, repositoryPath, baselineRevision, submitter,
                workspacePath, baselineManifestHash, repositoryMap, planHash, outcomeHash,
                Map.copyOf(invocationCopy), patch, List.copyOf(validationAttempts), validation,
                List.copyOf(approvals), rollback, List.copyOf(auditTrail), auditChainValid(), createdAt, updatedAt);
    }

    private boolean auditChainValid() {
        String previousHash = "GENESIS";
        long expectedSequence = 1L;
        for (AuditEvent event : auditTrail) {
            if (event.sequence() != expectedSequence || !previousHash.equals(event.previousHash())) {
                return false;
            }
            String expectedHash = sha256(event.sequence() + "|" + event.occurredAt() + "|" + event.type()
                    + "|" + event.actor() + "|" + event.details() + "|" + event.previousHash());
            if (!expectedHash.equals(event.eventHash())) {
                return false;
            }
            previousHash = event.eventHash();
            expectedSequence++;
        }
        return true;
    }

    private void audit(Instant now, String type, String actor, String details) {
        long sequence = auditTrail.size() + 1L;
        String previousHash = auditTrail.isEmpty() ? "GENESIS" : auditTrail.getLast().eventHash();
        String eventHash = sha256(sequence + "|" + now + "|" + type + "|" + actor + "|" + details + "|" + previousHash);
        auditTrail.add(new AuditEvent(sequence, now, type, actor, details, previousHash, eventHash));
        updatedAt = now;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
