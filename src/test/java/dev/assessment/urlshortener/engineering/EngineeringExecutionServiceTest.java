package dev.assessment.urlshortener.engineering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.EngineeringModels.BuildEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.BuildStatus;
import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringStatus;
import dev.assessment.urlshortener.engineering.EngineeringModels.HashApprovalRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.RequirementChangeRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.StartEngineeringRunRequest;
import dev.assessment.urlshortener.engineering.analysis.RepositoryAnalyzer;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import dev.assessment.urlshortener.engineering.model.ContextSanitizer;
import dev.assessment.urlshortener.engineering.model.DeterministicLlmClient;
import dev.assessment.urlshortener.engineering.model.StructuredAgentGateway;
import dev.assessment.urlshortener.engineering.patch.GovernedPatchApplier;
import dev.assessment.urlshortener.engineering.validation.BuildRunner;
import dev.assessment.urlshortener.engineering.workspace.RepositoryWorkspaceService;
import dev.assessment.urlshortener.governance.DefaultPolicyEngine;
import dev.assessment.urlshortener.orchestration.ScenarioType;
import dev.assessment.urlshortener.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringExecutionServiceTest {
    @TempDir Path temporary;

    @Test
    void executesRequirementSpecificPatchAndExactHashApprovalChain() throws IOException {
        EngineeringExecutionService service = service(passingBuild());
        var planned = service.start(new StartEngineeringRunRequest(
                "Create an expiry capability with executable tests and reviewer documentation.",
                ScenarioType.GREENFIELD, "repo", "WORKING_TREE"), "submitter");

        assertThat(planned.status()).isEqualTo(EngineeringStatus.AWAITING_CHANGE_APPROVAL);
        assertThat(planned.planHash()).hasSize(64);

        var validated = service.approveChange(planned.id(),
                new HashApprovalRequest(planned.planHash(), "Plan evidence reviewed"),
                "approver", List.of("ROLE_APPROVER"));

        assertThat(validated.status()).isEqualTo(EngineeringStatus.AWAITING_RELEASE_APPROVAL);
        assertThat(validated.validation().passed()).isTrue();
        assertThat(validated.validation().testsExecuted()).isTrue();
        assertThat(validated.patch().changedFiles()).anyMatch(path -> path.startsWith("src/main/java/"));
        assertThat(validated.patch().changedFiles()).anyMatch(path -> path.startsWith("src/test/java/"));
        assertThat(validated.patch().changedFiles()).anyMatch(path -> path.startsWith("docs/"));
        assertThat(validated.auditTrail()).extracting(EngineeringModels.AuditEvent::type)
                .contains("CHANGE_APPROVED", "BUILD_PASSED", "QUALITY_GATE_PASSED");

        var released = service.approveRelease(validated.id(),
                new HashApprovalRequest(validated.outcomeHash(), "Validated outcome reviewed"),
                "approver", List.of("ROLE_APPROVER"));
        assertThat(released.status()).isEqualTo(EngineeringStatus.RELEASE_READY);
        assertThat(service.metrics().releaseReadyRuns()).isEqualTo(1);
    }

    @Test
    void rejectsStaleHashAndSelfApproval() throws IOException {
        EngineeringExecutionService service = service(passingBuild());
        var run = service.start(new StartEngineeringRunRequest(
                "Create an auditable capability with tests.", ScenarioType.GREENFIELD, "repo", null), "submitter");

        assertThatThrownBy(() -> service.approveChange(run.id(),
                new HashApprovalRequest("stale", "reviewed"), "approver", List.of("ROLE_APPROVER")))
                .isInstanceOf(DomainException.class).hasMessageContaining("does not match");
        assertThatThrownBy(() -> service.approveChange(run.id(),
                new HashApprovalRequest(run.planHash(), "reviewed"), "submitter", List.of("ROLE_APPROVER")))
                .isInstanceOf(DomainException.class).hasMessageContaining("cannot approve");
    }

    @Test
    void failedBuildWithoutSafeRepairRestoresExactBaseline() throws IOException {
        EngineeringExecutionService service = service(workspace -> new BuildEvidence(
                BuildStatus.FAILED, "TEST", List.of("test"), 1, 10, false, 1, 1,
                Map.of("target/surefire-reports/failed.xml", "failed-report"),
                "compiler failure", "failure-hash"));
        var planned = service.start(new StartEngineeringRunRequest(
                "Create a normal capability that has a deliberately failed validation environment.",
                ScenarioType.GREENFIELD, "repo", null), "submitter");
        var stopped = service.approveChange(planned.id(),
                new HashApprovalRequest(planned.planHash(), "approved"),
                "approver", List.of("ROLE_APPROVER"));

        assertThat(stopped.status()).isEqualTo(EngineeringStatus.ROLLED_BACK);
        assertThat(stopped.rollback().restored()).isTrue();
        assertThat(stopped.rollback().restoredManifestHash()).isEqualTo(stopped.baselineManifestHash());
    }

    @Test
    void repairsOnceFromFailureEvidenceAndThenPasses() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        EngineeringExecutionService service = service(workspace -> attempts.getAndIncrement() == 0
                ? new BuildEvidence(BuildStatus.FAILED, "MAVEN_CLEAN_VERIFY", List.of("mvn", "verify"),
                        1, 15, false, 1, 1, Map.of("target/surefire-reports/failed.xml", "failed-report"),
                        "compiler failure in generated source", "failed-build")
                : new BuildEvidence(BuildStatus.PASSED, "MAVEN_CLEAN_VERIFY", List.of("mvn", "verify"),
                        0, 12, false, 1, 0, Map.of("target/surefire-reports/passed.xml", "passed-report"),
                        "Tests run: 1, Failures: 0, Errors: 0", "repaired-build"));

        var planned = service.start(new StartEngineeringRunRequest(
                "Create a repair-demo capability with executable tests and documentation.",
                ScenarioType.BROWNFIELD, "repo", null), "submitter");
        var validated = service.approveChange(planned.id(),
                new HashApprovalRequest(planned.planHash(), "approved"),
                "approver", List.of("ROLE_APPROVER"));

        assertThat(validated.status()).isEqualTo(EngineeringStatus.AWAITING_RELEASE_APPROVAL);
        assertThat(validated.validationAttempts()).hasSize(2);
        assertThat(validated.agentInvocations()).containsKey(EngineeringModels.AgentRole.REPAIR);
        assertThat(validated.validation().passed()).isTrue();
        assertThat(service.metrics().retryCount()).isEqualTo(1);
    }

    @Test
    void replanningInvalidatesPriorPlanAndRebuildsTheExecutionContext() throws IOException {
        EngineeringExecutionService service = service(passingBuild());
        var original = service.start(new StartEngineeringRunRequest(
                "Create an initial auditable capability with tests and documentation.",
                ScenarioType.BROWNFIELD, "repo", null), "submitter");

        var revised = service.replan(original.id(), new RequirementChangeRequest(
                "Create a revised auditable capability with a bounded expiry policy and tests.",
                "Product owner changed the upstream expiry rule"), "submitter");

        assertThat(revised.revision()).isEqualTo(2);
        assertThat(revised.planHash()).isNotEqualTo(original.planHash());
        assertThat(revised.patch()).isNull();
        assertThat(revised.approvals()).isEmpty();
        assertThat(revised.auditTrail()).extracting(EngineeringModels.AuditEvent::type)
                .contains("RUN_REPLANNED");
    }

    @Test
    void ambiguousRequirementStopsAtClarificationGate() throws IOException {
        EngineeringExecutionService service = service(passingBuild());
        var run = service.start(new StartEngineeringRunRequest(
                "Improve the service with whatever is best while preserving safe controls.",
                ScenarioType.AMBIGUOUS, "repo", null), "submitter");

        assertThat(run.status()).isEqualTo(EngineeringStatus.AWAITING_CLARIFICATION);
        assertThat(run.patch()).isNull();
        assertThat(run.planHash()).hasSize(64);
    }

    @Test
    void policyBlockedSecretIsRedactedBeforeDurableAudit() throws IOException {
        EngineeringExecutionService service = service(passingBuild());
        String secret = "abcdefghijk123456";

        var run = service.start(new StartEngineeringRunRequest(
                "Create a capability using api_key=" + secret,
                ScenarioType.GREENFIELD, "repo", null), "submitter");

        assertThat(run.status()).isEqualTo(EngineeringStatus.SAFE_STOPPED);
        assertThat(run.requirement()).doesNotContain(secret).contains("[REDACTED]");
        assertThat(run.auditTrail()).allSatisfy(event -> assertThat(event.details()).doesNotContain(secret));
        assertThat(run.auditChainValid()).isTrue();
    }

    private EngineeringExecutionService service(BuildRunner runner) throws IOException {
        Path repository = temporary.resolve("repo");
        Files.createDirectories(repository.resolve("src/main/java/example"));
        Files.writeString(repository.resolve("pom.xml"), "<project/>\n");
        Files.writeString(repository.resolve("src/main/java/example/Existing.java"),
                "package example; public final class Existing {}\n");
        AgenticExecutionProperties properties = EngineeringTestProperties.create(
                temporary, temporary.resolve("workspaces"), temporary.resolve("runs"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RepositoryWorkspaceService workspaces = new RepositoryWorkspaceService(properties);
        EngineeringRunStore store = new FileEngineeringRunStore(mapper, properties);
        var gateway = new StructuredAgentGateway(new DeterministicLlmClient(mapper), mapper, properties,
                Clock.systemUTC());
        return new EngineeringExecutionService(store, workspaces, new RepositoryAnalyzer(properties), gateway,
                new ContextSanitizer(properties),
                new GovernedPatchApplier(workspaces, properties, mapper), runner, new DefaultPolicyEngine(),
                properties, Clock.systemUTC());
    }

    private BuildRunner passingBuild() {
        return workspace -> new BuildEvidence(BuildStatus.PASSED, "MAVEN_CLEAN_VERIFY",
                List.of("mvn", "clean", "verify"), 0, 20, false, 1, 0,
                Map.of("target/surefire-reports/TEST-generated.xml", "report-hash"),
                "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0", "passing-hash");
    }
}
