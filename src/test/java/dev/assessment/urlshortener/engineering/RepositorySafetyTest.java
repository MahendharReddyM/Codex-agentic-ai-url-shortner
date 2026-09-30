package dev.assessment.urlshortener.engineering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.EngineeringModels.FileOperation;
import dev.assessment.urlshortener.engineering.EngineeringModels.FileOperationType;
import dev.assessment.urlshortener.engineering.analysis.RepositoryAnalyzer;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import dev.assessment.urlshortener.engineering.patch.GovernedPatchApplier;
import dev.assessment.urlshortener.engineering.patch.GovernedPatchApplier.PatchPolicyException;
import dev.assessment.urlshortener.engineering.workspace.RepositoryWorkspaceService;
import dev.assessment.urlshortener.engineering.workspace.RepositoryWorkspaceService.WorkspacePolicyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorySafetyTest {
    @TempDir Path temporary;

    @Test
    void mapsDifferentRepositoriesFromActualSourceEvidence() throws IOException {
        Path first = repository("first", "package alpha; public class AlphaService { public void shorten() {} }");
        Path second = repository("second", "package beta; public class BillingService { public void invoice() {} }");
        AgenticExecutionProperties properties = properties();
        RepositoryAnalyzer analyzer = new RepositoryAnalyzer(properties);

        var firstMap = analyzer.analyze(first, "Change shortening service behavior");
        var secondMap = analyzer.analyze(second, "Change shortening service behavior");

        assertThat(firstMap.repositoryHash()).isNotEqualTo(secondMap.repositoryHash());
        assertThat(firstMap.packages()).contains("alpha");
        assertThat(secondMap.packages()).contains("beta");
        assertThat(firstMap.symbols()).anyMatch(symbol -> symbol.endsWith("#AlphaService"));
        assertThat(firstMap.symbols()).anyMatch(symbol -> symbol.endsWith("#shorten()"));
    }

    @Test
    void blocksTraversalProtectedFilesAndGeneratedSecrets() throws IOException {
        repository("repo", "package example; public class Existing {}");
        AgenticExecutionProperties properties = properties();
        RepositoryWorkspaceService workspaces = new RepositoryWorkspaceService(properties);
        var evidence = workspaces.create(java.util.UUID.randomUUID(), 1, "repo");
        GovernedPatchApplier applier = new GovernedPatchApplier(workspaces, properties, new ObjectMapper());

        assertThatThrownBy(() -> workspaces.resolveForWrite(evidence.workspace(), "../escape.java"))
                .isInstanceOf(WorkspacePolicyException.class);
        assertThatThrownBy(() -> workspaces.resolveForWrite(evidence.workspace(), "pom.xml"))
                .isInstanceOf(WorkspacePolicyException.class);
        assertThatThrownBy(() -> applier.apply(evidence.workspace(), evidence.baseline(),
                evidence.baselineManifestHash(), List.of(new FileOperation(FileOperationType.CREATE,
                        "src/main/java/Leak.java", "class Leak { String apiKey=\"abcdefghijk\"; }", null,
                        "malicious proposal"))))
                .isInstanceOf(PatchPolicyException.class).hasMessageContaining("credential");
    }

    private Path repository(String name, String source) throws IOException {
        Path root = temporary.resolve(name);
        Files.createDirectories(root.resolve("src/main/java/example"));
        Files.writeString(root.resolve("pom.xml"), "<project><dependencies/></project>\n");
        Files.writeString(root.resolve("src/main/java/example/Example.java"), source);
        return root;
    }

    private AgenticExecutionProperties properties() {
        return EngineeringTestProperties.create(temporary, temporary.resolve("workspaces"), temporary.resolve("runs"));
    }
}
