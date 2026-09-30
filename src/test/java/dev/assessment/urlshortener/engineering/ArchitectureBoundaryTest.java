package dev.assessment.urlshortener.engineering;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
    @Test
    void untrustedModelAndPatchLayersCannotSpawnProcesses() throws IOException {
        for (Path root : List.of(Path.of("src/main/java/dev/assessment/urlshortener/engineering/model"),
                Path.of("src/main/java/dev/assessment/urlshortener/engineering/patch"))) {
            try (var files = Files.walk(root)) {
                for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(source);
                    assertThat(content)
                            .as("Only the fixed validation capability may execute a process: %s", source)
                            .doesNotContain("ProcessBuilder", "Runtime.getRuntime().exec");
                }
            }
        }
    }

    @Test
    void validationLayerDoesNotDependOnTheModelGateway() throws IOException {
        Path root = Path.of("src/main/java/dev/assessment/urlshortener/engineering/validation");
        try (var files = Files.walk(root)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(source))
                        .as("Validation must remain deterministic: %s", source)
                        .doesNotContain("engineering.model", "LlmClient", "StructuredAgentGateway");
            }
        }
    }
}
