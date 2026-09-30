package dev.assessment.urlshortener.engineering.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.assessment.urlshortener.engineering.EngineeringModels.BuildEvidence;
import dev.assessment.urlshortener.engineering.EngineeringModels.BuildStatus;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import org.springframework.stereotype.Service;

@Service
public class FixedBuildExecutor implements BuildRunner {
    private static final Pattern TEST_SUMMARY = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+)(?:, Skipped: (\\d+))?");
    private static final List<String> SENSITIVE_ENVIRONMENT_MARKERS = List.of(
            "KEY", "TOKEN", "SECRET", "PASSWORD", "CREDENTIAL", "AUTH");

    private final AgenticExecutionProperties properties;

    public FixedBuildExecutor(AgenticExecutionProperties properties) {
        this.properties = properties;
    }

    @Override
    public BuildEvidence verify(Path workspace) {
        Path normalized = workspace.toAbsolutePath().normalize();
        Command command = detect(normalized);
        if (command == null) {
            return evidence(BuildStatus.NOT_EXECUTED, "UNSUPPORTED", List.of(), -1, 0L, false, 0, 0,
                    "No supported Maven or Gradle wrapper was found", normalized);
        }
        Instant started = Instant.now();
        Process process = null;
        try (var outputExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            ProcessBuilder builder = new ProcessBuilder(command.arguments());
            builder.directory(normalized.toFile());
            builder.redirectErrorStream(true);
            scrubEnvironment(builder.environment());
            Process startedProcess = builder.start();
            process = startedProcess;
            Future<String> output = outputExecutor.submit(() -> readBounded(startedProcess.getInputStream()));
            boolean completed = startedProcess.waitFor(properties.commandTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            String captured = getOutput(output);
            long duration = Duration.between(started, Instant.now()).toMillis();
            if (!completed) {
                return evidence(BuildStatus.TIMED_OUT, command.capability(), command.arguments(), -1,
                        duration, true, tests(captured)[0], tests(captured)[1], captured, normalized);
            }
            int exit = process.exitValue();
            int[] testCounts = tests(captured);
            BuildStatus status = exit == 0 ? BuildStatus.PASSED : BuildStatus.FAILED;
            return evidence(status, command.capability(), command.arguments(), exit, duration, false,
                    testCounts[0], testCounts[1], captured, normalized);
        } catch (InterruptedException interrupted) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            return evidence(BuildStatus.TIMED_OUT, command.capability(), command.arguments(), -1,
                    Duration.between(started, Instant.now()).toMillis(), true, 0, 0,
                    "Build execution was interrupted", normalized);
        } catch (IOException exception) {
            return evidence(BuildStatus.NOT_EXECUTED, command.capability(), command.arguments(), -1,
                    Duration.between(started, Instant.now()).toMillis(), false, 0, 0,
                    "Build process could not start: " + exception.getMessage(), normalized);
        }
    }

    private Command detect(Path workspace) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        boolean wrapper = Files.isRegularFile(workspace.resolve(windows ? "mvnw.cmd" : "mvnw"));
        if (wrapper || Files.isRegularFile(workspace.resolve("pom.xml"))) {
            boolean systemMaven = System.getenv("MAVEN_HOME") != null;
            if (windows) {
                return new Command("MAVEN_CLEAN_VERIFY",
                        List.of("cmd.exe", "/d", "/s", "/c", wrapper && !systemMaven ? "mvnw.cmd" : "mvn",
                                "--batch-mode", "clean", "verify"));
            }
            return new Command("MAVEN_CLEAN_VERIFY",
                    List.of(wrapper && !systemMaven ? "./mvnw" : "mvn", "--batch-mode", "clean", "verify"));
        }
        if (Files.isRegularFile(workspace.resolve(windows ? "gradlew.bat" : "gradlew"))) {
            return new Command("GRADLE_CLEAN_TEST",
                    windows
                            ? List.of("cmd.exe", "/d", "/s", "/c", "gradlew.bat", "clean", "test")
                            : List.of("./gradlew", "clean", "test"));
        }
        return null;
    }

    private String readBounded(InputStream input) throws IOException {
        int limit = properties.commandOutputCharacters();
        byte[] buffer = new byte[8_192];
        int retained = 0;
        boolean truncated = false;
        var output = new java.io.ByteArrayOutputStream(Math.min(limit, 32_768));
        int read;
        while ((read = input.read(buffer)) >= 0) {
            int keep = Math.min(read, Math.max(0, limit - retained));
            if (keep > 0) {
                output.write(buffer, 0, keep);
                retained += keep;
            }
            if (keep < read) truncated = true;
        }
        String text = output.toString(StandardCharsets.UTF_8);
        return truncated ? text + "\n[BUILD_OUTPUT_TRUNCATED_BY_POLICY]" : text;
    }

    private String getOutput(Future<String> output) {
        try {
            return output.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "Build output collection was interrupted";
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            return "Build output collection failed: " + exception.getMessage();
        }
    }

    private void scrubEnvironment(Map<String, String> environment) {
        List<String> keys = new ArrayList<>(environment.keySet());
        for (String key : keys) {
            String upper = key.toUpperCase(Locale.ROOT);
            if (SENSITIVE_ENVIRONMENT_MARKERS.stream().anyMatch(upper::contains)) {
                environment.remove(key);
            }
        }
    }

    private int[] tests(String output) {
        Matcher matcher = TEST_SUMMARY.matcher(output);
        int tests = 0;
        int failures = 0;
        while (matcher.find()) {
            tests = Integer.parseInt(matcher.group(1));
            failures = Integer.parseInt(matcher.group(2)) + Integer.parseInt(matcher.group(3));
        }
        return new int[]{tests, failures};
    }

    private BuildEvidence evidence(
            BuildStatus status,
            String capability,
            List<String> command,
            int exitCode,
            long duration,
            boolean timedOut,
            int testsRun,
            int failures,
            String output,
            Path workspace) {
        return new BuildEvidence(status, capability, command, exitCode, duration, timedOut,
                testsRun, failures, reportHashes(workspace), output, sha256(output));
    }

    private Map<String, String> reportHashes(Path workspace) {
        Map<String, String> reports = new LinkedHashMap<>();
        for (Path root : List.of(workspace.resolve("target/surefire-reports"),
                workspace.resolve("build/test-results/test"))) {
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                List<Path> reportFiles = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".xml"))
                        .sorted()
                        .limit(500)
                        .toList();
                long totalBytes = 0;
                for (Path path : reportFiles) {
                    totalBytes += Files.size(path);
                    if (totalBytes > properties.maxRepositoryBytes()) {
                        throw new IllegalStateException("Test reports exceeded the configured evidence byte limit");
                    }
                    reports.put(workspace.relativize(path).toString().replace('\\', '/'), sha256File(path));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not collect test report evidence", exception);
            }
        }
        return Map.copyOf(reports);
    }

    private String sha256File(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash test report " + path.getFileName(), exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private record Command(String capability, List<String> arguments) {
    }
}
