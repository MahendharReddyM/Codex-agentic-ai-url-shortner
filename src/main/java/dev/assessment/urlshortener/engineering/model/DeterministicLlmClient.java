package dev.assessment.urlshortener.engineering.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.EngineeringModels.AgentEnvelope;
import dev.assessment.urlshortener.engineering.EngineeringModels.AgentRole;
import dev.assessment.urlshortener.engineering.EngineeringModels.FileOperation;
import dev.assessment.urlshortener.engineering.EngineeringModels.FileOperationType;

public final class DeterministicLlmClient implements LlmClient {
    private final ObjectMapper mapper;

    public DeterministicLlmClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public LlmResponse generate(AgentPrompt prompt) {
        AgentEnvelope output = switch (prompt.role()) {
            case REQUIREMENTS -> requirements(prompt);
            case ARCHITECTURE -> architecture(prompt);
            case IMPACT_ANALYSIS -> impact(prompt);
            case SECURITY_REVIEW -> security(prompt);
            case IMPLEMENTATION -> implementation(prompt);
            case TEST_GENERATION -> tests(prompt);
            case REPAIR -> repair(prompt);
            case DOCUMENTATION -> documentation(prompt);
            case RELEASE_READINESS -> release(prompt);
        };
        try {
            String content = mapper.writeValueAsString(output);
            return new LlmResponse(content, estimate(prompt.requirement()), estimate(content), "COMPLETED");
        } catch (JsonProcessingException exception) {
            throw new ModelInvocationException("Could not serialize deterministic model output", exception);
        }
    }

    @Override
    public String provider() {
        return "deterministic-test-double";
    }

    @Override
    public String model() {
        return "deterministic-v1";
    }

    private AgentEnvelope requirements(AgentPrompt prompt) {
        boolean ambiguous = prompt.scenario().name().equals("AMBIGUOUS")
                || prompt.requirement().toLowerCase().contains("whatever is best")
                || prompt.requirement().toLowerCase().contains("etc.");
        return envelope(
                "Normalized requirement " + shortId(prompt) + ": " + prompt.requirement(),
                List.of("Treat compilation, tests, security, documentation, rollback and approvals as exit criteria"),
                ambiguous ? List.of("Scope requires human clarification before source mutation") : List.of(),
                List.of(),
                Map.of("acceptance-criteria.md", "Requirement-specific acceptance criteria for: " + prompt.requirement()));
    }

    private AgentEnvelope architecture(AgentPrompt prompt) {
        String evidence = prompt.repositoryMap() == null
                ? "No repository map available"
                : "Build=" + prompt.repositoryMap().buildSystem() + ", relevant="
                        + String.join(", ", prompt.repositoryMap().relevantFiles());
        return envelope(
                "Designed a bounded change using repository evidence. " + evidence,
                List.of("Keep generation behind repository and patch ports", "Bind approvals to exact evidence hashes"),
                List.of("Generated changes remain untrusted until the fixed build capability passes"),
                List.of(),
                Map.of("architecture.md", evidence));
    }

    private AgentEnvelope impact(AgentPrompt prompt) {
        return envelope(
                "Mapped brownfield impact from " + prompt.repositoryMap().files().size() + " files and "
                        + prompt.repositoryMap().symbols().size() + " symbols.",
                List.of("Limit edits to evidence-backed paths", "Preserve public APIs unless acceptance criteria require change"),
                List.of("Static repository mapping may miss runtime-only coupling"),
                List.of(),
                Map.of("impact-analysis.json", String.join("\n", prompt.repositoryMap().relevantFiles())));
    }

    private AgentEnvelope security(AgentPrompt prompt) {
        return envelope(
                "Security review applied command, path, secret, output-size and approval boundaries.",
                List.of("Model output cannot execute commands", "Only fixed build capabilities may spawn processes"),
                List.of("Generated behavior still requires human review"),
                List.of(),
                Map.of("security-review.md", "Repository content is untrusted data; secrets and prompt injection are filtered."));
    }

    private AgentEnvelope implementation(AgentPrompt prompt) {
        String className = className(prompt);
        String path = "src/main/java/dev/assessment/generated/" + className + ".java";
        boolean repairDemo = prompt.requirement().toLowerCase().contains("repair-demo");
        String semicolon = repairDemo ? "" : ";";
        String content = "package dev.assessment.generated;\n\n"
                + "public final class " + className + " {\n"
                + "    private " + className + "() {}\n"
                + "    public static String requirementId() { return \"" + requirementId(prompt) + "\"; }\n"
                + "    public static String summary() { return \"" + javaEscape(prompt.requirement()) + "\"" + semicolon + " }\n"
                + "}\n";
        return envelope(
                "Generated a requirement-specific, isolated Java change for " + shortId(prompt),
                List.of("Use a unique source type so the proposal is additive and reviewable"),
                List.of("A real provider should implement domain behavior identified by repository analysis"),
                List.of(new FileOperation(FileOperationType.CREATE, path, content, null,
                        "Requirement-specific production source")),
                Map.of("implementation-plan.md", "Create " + path));
    }

    private AgentEnvelope tests(AgentPrompt prompt) {
        String className = className(prompt);
        String path = "src/test/java/dev/assessment/generated/" + className + "Test.java";
        String content = "package dev.assessment.generated;\n\n"
                + "import static org.assertj.core.api.Assertions.assertThat;\n"
                + "import org.junit.jupiter.api.Test;\n\n"
                + "class " + className + "Test {\n"
                + "    @Test void preservesRequirementLineage() {\n"
                + "        assertThat(" + className + ".requirementId()).isEqualTo(\"" + requirementId(prompt) + "\");\n"
                + "    }\n"
                + "}\n";
        return envelope(
                "Generated executable requirement-lineage coverage.",
                List.of("Tests must be executed by the fixed build capability, never marked passed by the model"),
                List.of(),
                List.of(new FileOperation(FileOperationType.CREATE, path, content, null,
                        "Executable test for generated production source")),
                Map.of("test-plan.md", "Compile and execute " + path));
    }

    private AgentEnvelope repair(AgentPrompt prompt) {
        String className = className(prompt);
        String path = "src/main/java/dev/assessment/generated/" + className + ".java";
        String expectedHash = prompt.repositoryMap().fileHashes().get(path);
        if (expectedHash == null || !prompt.requirement().toLowerCase().contains("repair-demo")) {
            return envelope("No safe deterministic repair is available.", List.of(),
                    List.of("Human intervention is required"), List.of(), Map.of());
        }
        String content = "package dev.assessment.generated;\n\n"
                + "public final class " + className + " {\n"
                + "    private " + className + "() {}\n"
                + "    public static String requirementId() { return \"" + requirementId(prompt) + "\"; }\n"
                + "    public static String summary() { return \"" + javaEscape(prompt.requirement()) + "\"; }\n"
                + "}\n";
        return envelope(
                "Repaired the controlled compilation defect using real failure evidence.",
                List.of("Modify only the failed generated source using its optimistic hash"),
                List.of(),
                List.of(new FileOperation(FileOperationType.UPDATE, path, content, expectedHash,
                        "Compiler-evidence-driven repair")),
                Map.of("repair-rationale.md", prompt.failureEvidence()));
    }

    private AgentEnvelope documentation(AgentPrompt prompt) {
        String path = "docs/generated/engineering-outcome-" + shortId(prompt) + ".md";
        String content = "# Generated engineering outcome\n\n"
                + "Requirement: " + prompt.requirement() + "\n\n"
                + "Repository evidence: " + String.join(", ", prompt.repositoryMap().relevantFiles()) + "\n";
        return envelope(
                "Generated requirement-specific reviewer documentation.",
                List.of("Keep generated documentation tied to repository evidence"),
                List.of(),
                List.of(new FileOperation(FileOperationType.CREATE, path, content, null,
                        "Reviewer-facing generated documentation")),
                Map.of("documentation-summary.md", content));
    }

    private AgentEnvelope release(AgentPrompt prompt) {
        return envelope(
                "Prepared release-readiness evidence without approving or deploying the change.",
                List.of("Only an authenticated human may approve the exact outcome hash"),
                List.of("Deployment remains outside agent authority"),
                List.of(),
                Map.of("release-readiness.md", String.join("\n", prompt.upstreamArtifacts().keySet())));
    }

    private AgentEnvelope envelope(
            String summary,
            List<String> decisions,
            List<String> risks,
            List<FileOperation> operations,
            Map<String, String> artifacts) {
        return new AgentEnvelope(summary, decisions, risks, operations, new LinkedHashMap<>(artifacts));
    }

    private String className(AgentPrompt prompt) {
        return "GeneratedOutcome" + prompt.runId().toString().replace("-", "").substring(0, 10)
                + "R" + prompt.revision();
    }

    private String requirementId(AgentPrompt prompt) {
        return Integer.toUnsignedString(prompt.requirement().hashCode(), 16);
    }

    private String shortId(AgentPrompt prompt) {
        return prompt.runId().toString().substring(0, 8) + "-r" + prompt.revision();
    }

    private String javaEscape(String value) {
        String bounded = value.substring(0, Math.min(value.length(), 500));
        return bounded.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }

    private int estimate(String value) {
        return Math.max(1, value.length() / 4);
    }

    public static class ModelInvocationException extends RuntimeException {
        public ModelInvocationException(String message) { super(message); }
        public ModelInvocationException(String message, Throwable cause) { super(message, cause); }
    }
}
