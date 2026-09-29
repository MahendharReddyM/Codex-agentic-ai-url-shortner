package dev.assessment.urlshortener.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WorkflowGraphFactory {

    public WorkflowGraph create(ScenarioType scenario) {
        List<StageDefinition> stages = new ArrayList<>();
        stages.add(stage("requirements", "Normalize requirement", StageType.REQUIREMENTS, Set.of(), false, 2));

        String architectureDependency = "requirements";
        if (scenario == ScenarioType.AMBIGUOUS) {
            stages.add(stage("ambiguity-approval", "Approve clarified scope", StageType.HUMAN_APPROVAL,
                    Set.of("requirements"), true, 1));
            architectureDependency = "ambiguity-approval";
        }

        stages.add(stage("architecture", "Design solution", StageType.ARCHITECTURE,
                Set.of(architectureDependency), false, 2));
        if (scenario == ScenarioType.BROWNFIELD) {
            stages.add(stage("impact-analysis", "Map impacted code and data flows", StageType.ARCHITECTURE,
                    Set.of("architecture"), false, 2));
            stages.add(stage("security-review", "Run policy and security review", StageType.SECURITY_REVIEW,
                    Set.of("impact-analysis"), false, 2));
        } else {
            stages.add(stage("security-review", "Run policy and security review", StageType.SECURITY_REVIEW,
                    Set.of("architecture"), false, 2));
        }
        stages.add(stage("design-approval", "Approve high-impact implementation", StageType.HUMAN_APPROVAL,
                Set.of("security-review"), true, 1));
        stages.add(stage("implementation", "Implement scoped change", StageType.IMPLEMENTATION,
                Set.of("design-approval"), false, 3));
        stages.add(stage("unit-tests", "Execute unit tests", StageType.TESTING,
                Set.of("implementation"), false, 2));
        stages.add(stage("integration-tests", "Execute integration tests", StageType.TESTING,
                Set.of("implementation"), false, 2));
        stages.add(stage("documentation", "Update engineering documentation", StageType.DOCUMENTATION,
                Set.of("implementation"), false, 2));
        stages.add(stage("quality-gate", "Synchronize and validate outputs", StageType.VALIDATION,
                Set.of("unit-tests", "integration-tests", "documentation"), false, 2));
        stages.add(stage("release-approval", "Approve release candidate", StageType.HUMAN_APPROVAL,
                Set.of("quality-gate"), true, 1));
        stages.add(stage("release-ready", "Generate release evidence", StageType.RELEASE,
                Set.of("release-approval"), false, 1));
        return new WorkflowGraph(stages, Set.of("requirements"), Set.of("release-ready"));
    }

    private StageDefinition stage(
            String id,
            String name,
            StageType type,
            Set<String> dependencies,
            boolean approval,
            int attempts) {
        return new StageDefinition(id, name, type, dependencies, approval, attempts, null);
    }
}

