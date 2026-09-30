package dev.assessment.urlshortener.engineering;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.orchestration.ScenarioType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringRunPersistenceTest {
    @TempDir Path temporary;

    @Test
    void reloadsRunAndAuditChainAfterStoreRestart() {
        var properties = EngineeringTestProperties.create(temporary,
                temporary.resolve("workspaces"), temporary.resolve("runs"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var firstProcess = new FileEngineeringRunStore(mapper, properties);
        EngineeringRun run = EngineeringRun.create(
                "Create a durable auditable engineering outcome with tests.",
                ScenarioType.GREENFIELD, "repo", "WORKING_TREE", "submitter", Instant.parse("2026-01-01T00:00:00Z"));
        firstProcess.save(run);

        var restartedProcess = new FileEngineeringRunStore(mapper, properties);
        var restored = restartedProcess.find(run.id()).orElseThrow().view();

        assertThat(restored.id()).isEqualTo(run.id());
        assertThat(restored.requirement()).isEqualTo(run.requirement());
        assertThat(restored.auditTrail()).hasSize(1);
        assertThat(restored.auditTrail().getFirst().eventHash()).hasSize(64);
        assertThat(restored.auditChainValid()).isTrue();
    }
}
