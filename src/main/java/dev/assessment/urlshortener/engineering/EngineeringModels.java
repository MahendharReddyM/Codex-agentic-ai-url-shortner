package dev.assessment.urlshortener.engineering;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.assessment.urlshortener.orchestration.ScenarioType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EngineeringModels {
    private EngineeringModels() {
    }

    public enum EngineeringStatus {
        PLANNING,
        AWAITING_CLARIFICATION,
        AWAITING_CHANGE_APPROVAL,
        EXECUTING,
        VALIDATING,
        AWAITING_RELEASE_APPROVAL,
        RELEASE_READY,
        SAFE_STOPPED,
        ROLLED_BACK,
        CANCELLED,
        FAILED
    }

    public enum AgentRole {
        REQUIREMENTS,
        ARCHITECTURE,
        IMPACT_ANALYSIS,
        SECURITY_REVIEW,
        IMPLEMENTATION,
        TEST_GENERATION,
        REPAIR,
        DOCUMENTATION,
        RELEASE_READINESS
    }

    public enum FileOperationType {
        CREATE,
        UPDATE,
        DELETE
    }

    public enum BuildStatus {
        PASSED,
        FAILED,
        TIMED_OUT,
        NOT_EXECUTED
    }

    public record StartEngineeringRunRequest(
            @NotBlank @Size(min = 10, max = 4_000) String requirement,
            @NotNull ScenarioType scenario,
            @NotBlank @Size(max = 500) String repositoryPath,
            @Size(max = 100) String baselineRevision) {
    }

    public record HashApprovalRequest(
            @NotBlank @Size(max = 128) String artifactHash,
            @NotBlank @Size(max = 1_000) String comment) {
    }

    public record RequirementChangeRequest(
            @NotBlank @Size(min = 10, max = 4_000) String requirement,
            @NotBlank @Size(max = 1_000) String reason) {
    }

    public record CancelEngineeringRunRequest(@NotBlank @Size(max = 1_000) String reason) {
    }

    public record RepositoryMap(
            String buildSystem,
            List<String> files,
            List<String> sourceFiles,
            List<String> testFiles,
            List<String> packages,
            List<String> symbols,
            List<String> dependencies,
            List<String> relevantFiles,
            Map<String, String> relevantSnippets,
            Map<String, String> fileHashes,
            String repositoryHash) {
        public RepositoryMap {
            files = List.copyOf(files);
            sourceFiles = List.copyOf(sourceFiles);
            testFiles = List.copyOf(testFiles);
            packages = List.copyOf(packages);
            symbols = List.copyOf(symbols);
            dependencies = List.copyOf(dependencies);
            relevantFiles = List.copyOf(relevantFiles);
            relevantSnippets = Map.copyOf(relevantSnippets);
            fileHashes = Map.copyOf(fileHashes);
        }
    }

    public record FileOperation(
            @NotNull FileOperationType operation,
            @NotBlank String relativePath,
            String content,
            String expectedSha256,
            @NotBlank String rationale) {
    }

    public record AgentEnvelope(
            @NotBlank String summary,
            List<String> decisions,
            List<String> risks,
            List<FileOperation> operations,
            Map<String, String> artifacts) {
        public AgentEnvelope {
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
            risks = risks == null ? List.of() : List.copyOf(risks);
            operations = operations == null ? List.of() : List.copyOf(operations);
            artifacts = artifacts == null ? Map.of() : Map.copyOf(artifacts);
        }
    }

    public record AgentInvocationEvidence(
            AgentRole role,
            int revision,
            String provider,
            String model,
            String promptVersion,
            Instant startedAt,
            Instant completedAt,
            long latencyMillis,
            Integer inputTokens,
            Integer outputTokens,
            String responseStatus,
            String inputHash,
            String outputHash,
            AgentEnvelope output) {
    }

    public record PatchEvidence(
            boolean applied,
            String proposalHash,
            String baselineManifestHash,
            String changedManifestHash,
            List<String> changedFiles,
            Map<String, String> artifactHashes,
            String unifiedDiff) {
        public PatchEvidence {
            changedFiles = List.copyOf(changedFiles);
            artifactHashes = Map.copyOf(artifactHashes);
        }
    }

    public record BuildEvidence(
            BuildStatus status,
            String capability,
            List<String> command,
            int exitCode,
            long durationMillis,
            boolean timedOut,
            int testsRun,
            int testFailures,
            Map<String, String> reportHashes,
            String output,
            String outputHash) {
        public BuildEvidence {
            command = List.copyOf(command);
            reportHashes = reportHashes == null ? Map.of() : Map.copyOf(reportHashes);
        }
    }

    public record ValidationEvidence(
            boolean patchApplied,
            boolean mainCodeCompiled,
            boolean testsExecuted,
            boolean requiredTestsPassed,
            boolean securityPassed,
            boolean documentationGenerated,
            boolean artifactsCurrent,
            String planHash,
            String outcomeHash,
            boolean passed,
            List<String> failures) {
        public ValidationEvidence {
            failures = List.copyOf(failures);
        }
    }

    public record ApprovalEvidence(
            String gate,
            int revision,
            String principal,
            List<String> roles,
            Instant decidedAt,
            String artifactHash,
            String comment) {
        public ApprovalEvidence {
            roles = List.copyOf(roles);
        }
    }

    public record RollbackEvidence(
            boolean attempted,
            boolean restored,
            String expectedManifestHash,
            String restoredManifestHash,
            Instant completedAt,
            String reason) {
    }

    public record AuditEvent(
            long sequence,
            Instant occurredAt,
            String type,
            String actor,
            String details,
            String previousHash,
            String eventHash) {
    }

    public record EngineeringRunView(
            UUID id,
            int revision,
            EngineeringStatus status,
            String requirement,
            ScenarioType scenario,
            String repositoryPath,
            String baselineRevision,
            String submitter,
            String workspacePath,
            String baselineManifestHash,
            RepositoryMap repositoryMap,
            String planHash,
            String outcomeHash,
            Map<AgentRole, List<AgentInvocationEvidence>> agentInvocations,
            PatchEvidence patch,
            List<BuildEvidence> validationAttempts,
            ValidationEvidence validation,
            List<ApprovalEvidence> approvals,
            RollbackEvidence rollback,
            List<AuditEvent> auditTrail,
            boolean auditChainValid,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record EngineeringReliabilityMetrics(
            int totalRuns,
            int releaseReadyRuns,
            int safeStoppedOrFailedRuns,
            double successRate,
            long retryCount,
            long rollbackCount,
            long meanRecoveryTimeMillis,
            long meanEndToEndLatencyMillis) {
    }
}
