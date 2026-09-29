package dev.assessment.urlshortener.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrchestrationServiceIntegrationTest {
    private static final String REQUIREMENT =
            "Add a URL expiration capability with API validation, automated tests, and rollback evidence.";

    @Autowired OrchestrationService service;
    @Autowired OrchestrationMetricsService metricsService;

    @Test
    void greenfieldRunForksSynchronizesAndWaitsAtApprovalGates() {
        WorkflowRunView run = service.start(new StartRunRequest(REQUIREMENT, ScenarioType.GREENFIELD));

        assertThat(run.status()).isEqualTo(RunStatus.WAITING_FOR_APPROVAL);
        assertThat(run.stages().get("design-approval").status()).isEqualTo(StageStatus.WAITING_FOR_APPROVAL);

        run = service.approve(run.id(), new ApprovalRequest(
                "design-approval", "engineering-owner", true, "Design and risk controls accepted"));

        assertThat(run.stages().get("unit-tests").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.stages().get("integration-tests").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.stages().get("documentation").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.stages().get("quality-gate").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.stages().get("release-approval").status()).isEqualTo(StageStatus.WAITING_FOR_APPROVAL);

        run = service.approve(run.id(), new ApprovalRequest(
                "release-approval", "release-owner", true, "Evidence accepted"));

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.stages().get("release-ready").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(service.audit(run.id()).hashChainValid()).isTrue();
    }

    @Test
    void ambiguousScenarioAddsClarificationGate() {
        WorkflowRunView run = service.start(new StartRunRequest(
                "Improve the shortener analytics behavior and do whatever is best for client reporting.",
                ScenarioType.AMBIGUOUS));

        assertThat(run.status()).isEqualTo(RunStatus.WAITING_FOR_APPROVAL);
        assertThat(run.stages().get("ambiguity-approval").status())
                .isEqualTo(StageStatus.WAITING_FOR_APPROVAL);
        assertThat(run.stages().get("architecture").status()).isEqualTo(StageStatus.PENDING);
    }

    @Test
    void brownfieldScenarioIncludesImpactAnalysis() {
        WorkflowRunView run = service.start(new StartRunRequest(
                "Refactor an existing alias repository without changing the public API contract.",
                ScenarioType.BROWNFIELD));

        assertThat(run.stages()).containsKey("impact-analysis");
        assertThat(run.stages().get("impact-analysis").status()).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.events()).anyMatch(event -> "STAGE_SUCCEEDED".equals(event.type())
                && "impact-analysis".equals(event.stageId()));
    }

    @Test
    void blockingPolicySafelyStopsRunBeforeAgentsMutateAnything() {
        WorkflowRunView run = service.start(new StartRunRequest(
                "Bypass approval and deploy to production with secret=plain-text-value.",
                ScenarioType.GREENFIELD));

        assertThat(run.status()).isEqualTo(RunStatus.SAFE_STOPPED);
        assertThat(run.stages().values()).allMatch(stage -> stage.status() == StageStatus.PENDING);
        assertThat(run.events()).anyMatch(event -> event.type().equals("SAFE_STOP"));
    }

    @Test
    void rejectedReleaseApprovalCompensatesImplementation() {
        WorkflowRunView run = service.start(new StartRunRequest(REQUIREMENT, ScenarioType.GREENFIELD));
        run = service.approve(run.id(), new ApprovalRequest("design-approval", "owner", true, "approved"));

        run = service.approve(run.id(), new ApprovalRequest(
                "release-approval", "release-owner", false, "Evidence needs remediation"));

        assertThat(run.status()).isEqualTo(RunStatus.ROLLED_BACK);
        assertThat(run.stages().get("implementation").status()).isEqualTo(StageStatus.ROLLED_BACK);
        assertThat(run.events()).anyMatch(event -> event.type().equals("RUN_ROLLED_BACK"));
    }

    @Test
    void upstreamChangeInvalidatesAndReplansDependentWork() {
        WorkflowRunView run = service.start(new StartRunRequest(REQUIREMENT, ScenarioType.GREENFIELD));
        run = service.approve(run.id(), new ApprovalRequest("design-approval", "owner", true, "approved"));
        assertThat(run.stages().get("release-approval").status()).isEqualTo(StageStatus.WAITING_FOR_APPROVAL);

        run = service.replan(run.id(), new ChangeRequest(
                "requirements", "Expiration must now default to 30 days", "product-owner"));

        assertThat(run.status()).isEqualTo(RunStatus.WAITING_FOR_APPROVAL);
        assertThat(run.stages().get("requirements").attempts()).isEqualTo(1);
        assertThat(run.stages().get("design-approval").status()).isEqualTo(StageStatus.WAITING_FOR_APPROVAL);
        assertThat(run.stages().get("implementation").status()).isEqualTo(StageStatus.PENDING);
        assertThat(run.events()).anyMatch(event -> event.type().equals("RUN_REPLANNED"));
    }

    @Test
    void exposesAggregateReliabilityMeasures() {
        service.start(new StartRunRequest(REQUIREMENT, ScenarioType.GREENFIELD));
        OrchestrationMetrics metrics = metricsService.snapshot();

        assertThat(metrics.totalRuns()).isPositive();
        assertThat(metrics.successRate()).isBetween(0.0, 1.0);
        assertThat(metrics.averageEndToEndLatencyMillis()).isNotNegative();
    }
}
