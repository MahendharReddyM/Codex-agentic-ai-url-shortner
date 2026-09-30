package dev.assessment.urlshortener.engineering.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import org.springframework.stereotype.Service;

@Service
public class RepositoryWorkspaceService {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", "target", "build", ".gradle", "work", "data", "node_modules");
    private static final Pattern SAFE_GIT_REVISION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,99}");

    private final Path repositoryRoot;
    private final Path workspaceRoot;
    private final AgenticExecutionProperties properties;

    public RepositoryWorkspaceService(AgenticExecutionProperties properties) {
        this.properties = properties;
        this.repositoryRoot = properties.repositoryRoot().toAbsolutePath().normalize();
        this.workspaceRoot = properties.workspaceRoot().toAbsolutePath().normalize();
    }

    public WorkspaceEvidence create(UUID runId, int revision, String requestedRepository) {
        return create(runId, revision, requestedRepository, "WORKING_TREE");
    }

    public WorkspaceEvidence create(
            UUID runId,
            int revision,
            String requestedRepository,
            String baselineRevision) {
        Path source = resolveRepository(requestedRepository);
        Path runRoot = workspaceRoot.resolve(runId.toString()).resolve("revision-" + revision).normalize();
        ensureWithin(workspaceRoot, runRoot, "workspace");
        Path baseline = runRoot.resolve("baseline");
        Path workspace = runRoot.resolve("workspace");
        if (Files.exists(runRoot)) {
            throw new WorkspacePolicyException("Workspace revision already exists: " + revision);
        }
        try {
            Files.createDirectories(baseline);
            if (baselineRevision == null || baselineRevision.isBlank()
                    || "WORKING_TREE".equalsIgnoreCase(baselineRevision)) {
                copyTree(source, baseline);
            } else {
                exportRevision(source, runRoot, baseline, baselineRevision);
            }
            copyTree(baseline, workspace);
            String manifest = manifest(baseline);
            return new WorkspaceEvidence(source, workspace, baseline, manifest, Instant.now());
        } catch (IOException | RuntimeException exception) {
            safeDelete(runRoot);
            if (exception instanceof WorkspacePolicyException policyException) {
                throw policyException;
            }
            throw new WorkspacePolicyException("Could not create isolated repository workspace", exception);
        }
    }

    private void exportRevision(Path source, Path runRoot, Path baseline, String revision) throws IOException {
        if (!SAFE_GIT_REVISION.matcher(revision).matches() || revision.contains("..")) {
            throw new WorkspacePolicyException("Git baseline revision contains unsupported characters");
        }
        if (!Files.isDirectory(source.resolve(".git"))) {
            throw new WorkspacePolicyException("A named baseline revision requires a local Git repository");
        }
        Path archive = runRoot.resolve("baseline.zip").normalize();
        ensureWithin(runRoot, archive, "revision archive");
        Process process = null;
        try {
            process = new ProcessBuilder(List.of("git", "-C", source.toString(), "archive", "--format=zip",
                    "--output=" + archive, revision))
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(properties.commandTimeout().toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readNBytes(8_192), StandardCharsets.UTF_8);
            if (!completed) {
                process.destroyForcibly();
                throw new WorkspacePolicyException("Git baseline export timed out");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(archive)) {
                throw new WorkspacePolicyException("Git baseline export failed: " + output.strip());
            }
            extractArchive(archive, baseline);
        } catch (InterruptedException interrupted) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new WorkspacePolicyException("Git baseline export was interrupted", interrupted);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private void extractArchive(Path archive, Path destination) throws IOException {
        long fileCount = 0;
        long byteCount = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                ensureWithin(destination, target, "archive entry");
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    fileCount++;
                    if (fileCount > properties.maxRepositoryFiles()) {
                        throw new WorkspacePolicyException("Git revision exceeds configured file limit");
                    }
                    Files.createDirectories(target.getParent());
                    byte[] content = readArchiveEntry(zip, properties.maxRepositoryBytes() - byteCount);
                    byteCount += content.length;
                    Files.write(target, content);
                }
                zip.closeEntry();
            }
        }
    }

    public RollbackResult rollback(Path workspace, Path baseline, String expectedManifestHash) {
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        Path normalizedBaseline = baseline.toAbsolutePath().normalize();
        ensureWithin(workspaceRoot, normalizedWorkspace, "workspace");
        ensureWithin(workspaceRoot, normalizedBaseline, "baseline");
        safeDelete(normalizedWorkspace);
        try {
            copyTree(normalizedBaseline, normalizedWorkspace);
            String restored = manifest(normalizedWorkspace);
            return new RollbackResult(expectedManifestHash, restored, expectedManifestHash.equals(restored));
        } catch (IOException exception) {
            throw new WorkspacePolicyException("Could not restore baseline workspace", exception);
        }
    }

    public String manifest(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        ensureWithin(workspaceRoot, normalized, "manifest target");
        try {
            List<Path> files;
            try (var stream = Files.walk(normalized)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(path -> included(normalized, path))
                        .sorted(Comparator.comparing(path -> normalized.relativize(path).toString()))
                        .toList();
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path file : files) {
                digest.update(normalized.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(file));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new WorkspacePolicyException("Could not create repository manifest", exception);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    public DiffEvidence diff(Path baseline, Path workspace) {
        Map<String, byte[]> before = filesByPath(baseline);
        Map<String, byte[]> after = filesByPath(workspace);
        List<String> paths = new ArrayList<>();
        paths.addAll(before.keySet());
        after.keySet().stream().filter(path -> !before.containsKey(path)).forEach(paths::add);
        paths.sort(String::compareTo);
        List<String> changed = paths.stream()
                .filter(path -> !java.util.Arrays.equals(before.get(path), after.get(path)))
                .toList();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String path : changed) {
            byte[] newBytes = after.get(path);
            hashes.put(path, newBytes == null ? "DELETED" : sha256(newBytes));
        }
        StringBuilder unified = new StringBuilder();
        for (String path : changed) {
            byte[] oldBytes = before.get(path);
            byte[] newBytes = after.get(path);
            appendWholeFileDiff(unified, path, oldBytes, newBytes);
            if (unified.length() > properties.maxOutputCharacters()) {
                unified.setLength(properties.maxOutputCharacters());
                unified.append("\n... diff truncated by policy ...\n");
                break;
            }
        }
        return new DiffEvidence(changed, hashes, unified.toString(), manifest(workspace));
    }

    public Path resolveForWrite(Path workspace, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new WorkspacePolicyException("Patch path is required");
        }
        Path requested = Path.of(relativePath.replace('\\', '/'));
        if (requested.isAbsolute() || relativePath.contains("\0")) {
            throw new WorkspacePolicyException("Patch path must be relative");
        }
        Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
        ensureWithin(workspaceRoot, normalizedWorkspace, "workspace");
        Path target = normalizedWorkspace.resolve(requested).normalize();
        ensureWithin(normalizedWorkspace, target, "patch target");
        String portable = normalizedWorkspace.relativize(target).toString().replace('\\', '/');
        if (portable.startsWith(".git/") || portable.equals(".git")
                || portable.startsWith(".github/workflows/")
                || portable.equals("pom.xml") || portable.equals("mvnw") || portable.equals("mvnw.cmd")) {
            throw new WorkspacePolicyException("Patch target is protected: " + portable);
        }
        return target;
    }

    private Path resolveRepository(String requestedRepository) {
        Path requested = Path.of(requestedRepository);
        if (requested.isAbsolute() || requestedRepository.contains("\0")) {
            throw new WorkspacePolicyException("Repository path must be relative to the approved root");
        }
        Path source = repositoryRoot.resolve(requested).normalize();
        ensureWithin(repositoryRoot, source, "repository");
        if (!Files.isDirectory(source)) {
            throw new WorkspacePolicyException("Repository path does not exist or is not a directory");
        }
        return source;
    }

    private void copyTree(Path source, Path destination) throws IOException {
        long[] fileCount = {0};
        long[] byteCount = {0};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new WorkspacePolicyException("Symbolic links are not allowed: " + directory);
                }
                if (!directory.equals(source) && EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path target = destination.resolve(source.relativize(directory).toString()).normalize();
                ensureWithin(destination, target, "copy destination");
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new WorkspacePolicyException("Symbolic links are not allowed: " + file);
                }
                fileCount[0]++;
                byteCount[0] += attributes.size();
                if (fileCount[0] > properties.maxRepositoryFiles()
                        || byteCount[0] > properties.maxRepositoryBytes()) {
                    throw new WorkspacePolicyException("Repository exceeds configured file or byte limits");
                }
                Path target = destination.resolve(source.relativize(file).toString()).normalize();
                ensureWithin(destination, target, "copy destination");
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Map<String, byte[]> filesByPath(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        ensureWithin(workspaceRoot, normalized, "diff root");
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var stream = Files.walk(normalized)) {
            stream.filter(Files::isRegularFile).filter(path -> included(normalized, path)).sorted().forEach(path -> {
                try {
                    files.put(normalized.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
                } catch (IOException exception) {
                    throw new WorkspacePolicyException("Could not read repository file", exception);
                }
            });
            return files;
        } catch (IOException exception) {
            throw new WorkspacePolicyException("Could not inspect repository tree", exception);
        }
    }

    private void appendWholeFileDiff(StringBuilder diff, String path, byte[] oldBytes, byte[] newBytes) {
        diff.append("--- ").append(oldBytes == null ? "/dev/null" : "a/" + path).append('\n');
        diff.append("+++ ").append(newBytes == null ? "/dev/null" : "b/" + path).append('\n');
        String oldText = oldBytes == null ? "" : new String(oldBytes, StandardCharsets.UTF_8);
        String newText = newBytes == null ? "" : new String(newBytes, StandardCharsets.UTF_8);
        for (String line : oldText.split("\\R", -1)) {
            if (!line.isEmpty()) diff.append('-').append(line).append('\n');
        }
        for (String line : newText.split("\\R", -1)) {
            if (!line.isEmpty()) diff.append('+').append(line).append('\n');
        }
    }

    private byte[] readArchiveEntry(ZipInputStream zip, long remainingBytes) throws IOException {
        if (remainingBytes < 0) {
            throw new WorkspacePolicyException("Git revision exceeds configured byte limit");
        }
        var output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        long readTotal = 0;
        int read;
        while ((read = zip.read(buffer)) >= 0) {
            readTotal += read;
            if (readTotal > remainingBytes) {
                throw new WorkspacePolicyException("Git revision exceeds configured byte limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean included(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString())) return false;
        }
        return true;
    }

    private void safeDelete(Path target) {
        if (target == null || !Files.exists(target)) return;
        Path normalized = target.toAbsolutePath().normalize();
        ensureWithin(workspaceRoot, normalized, "delete target");
        if (normalized.equals(workspaceRoot)) {
            throw new WorkspacePolicyException("Refusing to delete workspace root");
        }
        try (var stream = Files.walk(normalized)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new WorkspacePolicyException("Could not clean workspace path", exception);
                }
            });
        } catch (IOException exception) {
            throw new WorkspacePolicyException("Could not traverse workspace for cleanup", exception);
        }
    }

    private void ensureWithin(Path root, Path candidate, String label) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new WorkspacePolicyException(label + " escapes its approved root");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    public record WorkspaceEvidence(
            Path source,
            Path workspace,
            Path baseline,
            String baselineManifestHash,
            Instant createdAt) {
    }

    public record RollbackResult(String expectedManifestHash, String restoredManifestHash, boolean restored) {
    }

    public record DiffEvidence(
            List<String> changedFiles,
            Map<String, String> artifactHashes,
            String unifiedDiff,
            String manifestHash) {
    }

    public static class WorkspacePolicyException extends RuntimeException {
        public WorkspacePolicyException(String message) { super(message); }
        public WorkspacePolicyException(String message, Throwable cause) { super(message, cause); }
    }
}
