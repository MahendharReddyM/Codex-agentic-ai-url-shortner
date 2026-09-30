package dev.assessment.urlshortener.engineering.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.assessment.urlshortener.engineering.EngineeringModels.RepositoryMap;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import org.springframework.stereotype.Service;

@Service
public class RepositoryAnalyzer {
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".xml", ".yml", ".yaml", ".json", ".properties",
            ".md", ".gradle", ".groovy", ".sql", ".txt", ".http");
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", "target", "build", ".gradle", "work", "data", "node_modules");
    private static final Set<String> STOP_WORDS = Set.of(
            "with", "from", "into", "that", "this", "should", "must", "change", "feature",
            "add", "create", "update", "make", "without", "existing", "requirement");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern METHOD = Pattern.compile(
            "(?m)\\b(?:public|protected|private)\\s+(?:static\\s+)?[A-Za-z0-9_$.<>?, \\[\\]]+\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\(");
    private static final Pattern MAVEN_DEPENDENCY = Pattern.compile(
            "<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);

    private final AgenticExecutionProperties properties;

    public RepositoryAnalyzer(AgenticExecutionProperties properties) {
        this.properties = properties;
    }

    public RepositoryMap analyze(Path workspace, String requirement) {
        Path root = workspace.toAbsolutePath().normalize();
        List<Path> regularFiles;
        try (var stream = Files.walk(root)) {
            regularFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> included(root, path))
                    .sorted(Comparator.comparing(path -> portable(root, path)))
                    .limit(properties.maxRepositoryFiles() + 1L)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not walk repository workspace", exception);
        }
        if (regularFiles.size() > properties.maxRepositoryFiles()) {
            throw new IllegalArgumentException("Repository exceeds analysis file limit");
        }

        List<String> files = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<String> tests = new ArrayList<>();
        Set<String> packages = new LinkedHashSet<>();
        Set<String> symbols = new LinkedHashSet<>();
        Set<String> dependencies = new LinkedHashSet<>();
        Map<String, String> readable = new LinkedHashMap<>();
        Map<String, String> fileHashes = new LinkedHashMap<>();
        long totalBytes = 0L;
        for (Path file : regularFiles) {
            String relative = portable(root, file);
            files.add(relative);
            byte[] bytes = read(file);
            totalBytes += bytes.length;
            if (totalBytes > properties.maxRepositoryBytes()) {
                throw new IllegalArgumentException("Repository exceeds analysis byte limit");
            }
            fileHashes.put(relative, sha256(bytes));
            if (relative.contains("/src/main/") || relative.startsWith("src/main/")) sources.add(relative);
            if (relative.contains("/src/test/") || relative.startsWith("src/test/")) tests.add(relative);
            if (!isText(relative) || bytes.length > 250_000) continue;
            String content = new String(bytes, StandardCharsets.UTF_8);
            readable.put(relative, content);
            collectJavaEvidence(relative, content, packages, symbols);
            collectDependencies(relative, content, dependencies);
        }

        Set<String> requirementTerms = terms(requirement);
        List<String> relevant = readable.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), relevance(entry.getKey(), entry.getValue(), requirementTerms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(20)
                .map(Map.Entry::getKey)
                .toList();
        if (relevant.isEmpty()) {
            relevant = sources.stream().limit(12).toList();
        }
        Map<String, String> snippets = boundedSnippets(relevant, readable);
        String buildSystem = detectBuild(files);
        String repositoryHash = sha256(fileHashes.toString().getBytes(StandardCharsets.UTF_8));
        return new RepositoryMap(buildSystem, files, sources, tests, List.copyOf(packages),
                List.copyOf(symbols), List.copyOf(dependencies), relevant, snippets, fileHashes, repositoryHash);
    }

    private void collectJavaEvidence(
            String path,
            String content,
            Set<String> packages,
            Set<String> symbols) {
        Matcher packageMatcher = PACKAGE.matcher(content);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
        if (!packageName.isBlank()) packages.add(packageName);
        Matcher typeMatcher = TYPE.matcher(content);
        while (typeMatcher.find()) {
            symbols.add(path + "#" + typeMatcher.group(1));
        }
        Matcher methodMatcher = METHOD.matcher(content);
        while (methodMatcher.find() && symbols.size() < 500) {
            symbols.add(path + "#" + methodMatcher.group(1) + "()");
        }
    }

    private void collectDependencies(String path, String content, Set<String> dependencies) {
        if (path.endsWith("pom.xml")) {
            Matcher matcher = MAVEN_DEPENDENCY.matcher(content);
            while (matcher.find() && dependencies.size() < 200) {
                dependencies.add(matcher.group(1).trim() + ":" + matcher.group(2).trim());
            }
        }
        if (path.endsWith("build.gradle") || path.endsWith("build.gradle.kts")) {
            content.lines().map(String::trim)
                    .filter(line -> line.startsWith("implementation") || line.startsWith("testImplementation"))
                    .limit(100)
                    .forEach(dependencies::add);
        }
    }

    private Map<String, String> boundedSnippets(List<String> relevant, Map<String, String> readable) {
        Map<String, String> snippets = new LinkedHashMap<>();
        int remaining = properties.maxContextCharacters();
        for (String path : relevant) {
            String content = readable.get(path);
            if (content == null || remaining <= 0) continue;
            int limit = Math.min(Math.min(content.length(), 8_000), remaining);
            snippets.put(path, content.substring(0, limit));
            remaining -= limit;
        }
        return snippets;
    }

    private int relevance(String path, String content, Set<String> terms) {
        String searchable = (path + "\n" + content.substring(0, Math.min(content.length(), 40_000)))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (searchable.contains(term)) score++;
            if (path.toLowerCase(Locale.ROOT).contains(term)) score += 3;
        }
        return score;
    }

    private Set<String> terms(String requirement) {
        Set<String> terms = new HashSet<>();
        for (String term : requirement.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (term.length() >= 4 && !STOP_WORDS.contains(term)) terms.add(term);
        }
        return terms;
    }

    private String detectBuild(List<String> files) {
        if (files.contains("pom.xml")) return "MAVEN";
        if (files.contains("build.gradle") || files.contains("build.gradle.kts")) return "GRADLE";
        return "UNKNOWN";
    }

    private boolean isText(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("pom.xml") || lower.endsWith("dockerfile") || lower.endsWith("mvnw") || lower.endsWith("mvnw.cmd")) {
            return true;
        }
        return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read repository file " + path, exception);
        }
    }

    private String portable(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private boolean included(Path root, Path path) {
        for (Path segment : root.relativize(path)) {
            if (GENERATED_DIRECTORIES.contains(segment.toString())) return false;
        }
        return true;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
