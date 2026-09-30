package dev.assessment.urlshortener.engineering.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agentic")
public record AgenticExecutionProperties(
        String provider,
        String model,
        String openaiBaseUrl,
        String openaiApiKey,
        Path repositoryRoot,
        Path workspaceRoot,
        Path persistenceRoot,
        String persistence,
        String jdbcUrl,
        String jdbcUser,
        String jdbcPassword,
        Duration modelTimeout,
        Duration commandTimeout,
        int maxContextCharacters,
        int maxOutputCharacters,
        int maxRepositoryFiles,
        long maxRepositoryBytes,
        int maxPatchFiles,
        long maxPatchBytes,
        int maxAttempts,
        int commandOutputCharacters) {

    public AgenticExecutionProperties {
        provider = textOr(provider, "deterministic");
        model = textOr(model, "gpt-5");
        openaiBaseUrl = textOr(openaiBaseUrl, "https://api.openai.com/v1");
        openaiApiKey = openaiApiKey == null ? "" : openaiApiKey;
        repositoryRoot = repositoryRoot == null ? Path.of(".") : repositoryRoot;
        workspaceRoot = workspaceRoot == null ? Path.of("work/engineering-runs") : workspaceRoot;
        persistenceRoot = persistenceRoot == null ? Path.of("data/engineering-runs") : persistenceRoot;
        persistence = textOr(persistence, "file");
        jdbcUrl = textOr(jdbcUrl, "jdbc:postgresql://localhost:5432/agentic");
        jdbcUser = textOr(jdbcUser, "agentic");
        jdbcPassword = jdbcPassword == null ? "" : jdbcPassword;
        modelTimeout = positive(modelTimeout, Duration.ofSeconds(45));
        commandTimeout = positive(commandTimeout, Duration.ofMinutes(5));
        maxContextCharacters = positive(maxContextCharacters, 60_000);
        maxOutputCharacters = positive(maxOutputCharacters, 60_000);
        maxRepositoryFiles = positive(maxRepositoryFiles, 3_000);
        maxRepositoryBytes = positive(maxRepositoryBytes, 25_000_000L);
        maxPatchFiles = positive(maxPatchFiles, 30);
        maxPatchBytes = positive(maxPatchBytes, 1_000_000L);
        maxAttempts = maxAttempts > 0 ? Math.min(5, maxAttempts) : 3;
        commandOutputCharacters = positive(commandOutputCharacters, 120_000);
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
    }
}
