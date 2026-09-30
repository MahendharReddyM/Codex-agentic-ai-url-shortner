package dev.assessment.urlshortener.engineering;

import java.nio.file.Path;
import java.time.Duration;

import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;

final class EngineeringTestProperties {
    private EngineeringTestProperties() {
    }

    static AgenticExecutionProperties create(Path repositoryRoot, Path workspaceRoot, Path persistenceRoot) {
        return new AgenticExecutionProperties(
                "deterministic", "deterministic-v1", "https://api.openai.com/v1", "",
                repositoryRoot, workspaceRoot, persistenceRoot,
                "file", "jdbc:postgresql://localhost/test", "test", "",
                Duration.ofSeconds(2), Duration.ofSeconds(10),
                20_000, 20_000, 500, 5_000_000,
                20, 500_000, 3, 20_000);
    }
}
