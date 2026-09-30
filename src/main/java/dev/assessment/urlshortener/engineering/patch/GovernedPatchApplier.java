package dev.assessment.urlshortener.engineering.patch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.EngineeringModels.FileOperation;
import dev.assessment.urlshortener.engineering.EngineeringModels.FileOperationType;
import dev.assessment.urlshortener.engineering.EngineeringModels.PatchEvidence;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import dev.assessment.urlshortener.engineering.workspace.RepositoryWorkspaceService;
import org.springframework.stereotype.Service;

@Service
public class GovernedPatchApplier {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".xml", ".yml", ".yaml", ".json", ".properties",
            ".md", ".sql", ".txt", ".http");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|token)\\s*[:=]\\s*['\"]?[^\\s'\",;]{8,}");

    private final RepositoryWorkspaceService workspaces;
    private final AgenticExecutionProperties properties;
    private final ObjectMapper mapper;

    public GovernedPatchApplier(
            RepositoryWorkspaceService workspaces,
            AgenticExecutionProperties properties,
            ObjectMapper mapper) {
        this.workspaces = workspaces;
        this.properties = properties;
        this.mapper = mapper;
    }

    public PatchEvidence apply(
            Path workspace,
            Path baseline,
            String baselineManifestHash,
            List<FileOperation> operations) {
        validate(operations);
        String proposalHash = proposalHash(operations);
        try {
            for (FileOperation operation : operations) {
                applyOne(workspace, operation);
            }
            var diff = workspaces.diff(baseline, workspace);
            if (diff.changedFiles().isEmpty()) {
                throw new PatchPolicyException("Patch proposal did not change the workspace");
            }
            return new PatchEvidence(true, proposalHash, baselineManifestHash, diff.manifestHash(),
                    diff.changedFiles(), diff.artifactHashes(), diff.unifiedDiff());
        } catch (RuntimeException exception) {
            try {
                workspaces.rollback(workspace, baseline, baselineManifestHash);
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    public PatchEvidence applyRepair(
            Path workspace,
            Path baseline,
            String baselineManifestHash,
            List<FileOperation> operations) {
        validate(operations);
        for (FileOperation operation : operations) {
            applyOne(workspace, operation);
        }
        var diff = workspaces.diff(baseline, workspace);
        return new PatchEvidence(true, proposalHash(operations), baselineManifestHash, diff.manifestHash(),
                diff.changedFiles(), diff.artifactHashes(), diff.unifiedDiff());
    }

    private void applyOne(Path workspace, FileOperation operation) {
        Path target = workspaces.resolveForWrite(workspace, operation.relativePath());
        String beforeHash = Files.isRegularFile(target) ? sha256(read(target)) : null;
        switch (operation.operation()) {
            case CREATE -> {
                if (Files.exists(target)) throw new PatchPolicyException("CREATE target already exists: " + operation.relativePath());
                writeAtomically(target, operation.content());
            }
            case UPDATE -> {
                if (beforeHash == null) throw new PatchPolicyException("UPDATE target does not exist: " + operation.relativePath());
                if (operation.expectedSha256() == null || !operation.expectedSha256().equals(beforeHash)) {
                    throw new PatchPolicyException("Optimistic hash mismatch for " + operation.relativePath());
                }
                writeAtomically(target, operation.content());
            }
            case DELETE -> {
                if (beforeHash == null) throw new PatchPolicyException("DELETE target does not exist: " + operation.relativePath());
                if (operation.expectedSha256() == null || !operation.expectedSha256().equals(beforeHash)) {
                    throw new PatchPolicyException("Optimistic hash mismatch for " + operation.relativePath());
                }
                try {
                    Files.delete(target);
                } catch (IOException exception) {
                    throw new PatchPolicyException("Could not delete " + operation.relativePath(), exception);
                }
            }
        }
    }

    private void validate(List<FileOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            throw new PatchPolicyException("Patch proposal must contain at least one operation");
        }
        if (operations.size() > properties.maxPatchFiles()) {
            throw new PatchPolicyException("Patch proposal exceeds file-operation limit");
        }
        long totalBytes = 0L;
        Set<String> paths = new HashSet<>();
        for (FileOperation operation : operations) {
            String path = operation.relativePath().replace('\\', '/');
            if (!paths.add(path)) throw new PatchPolicyException("Duplicate patch path: " + path);
            if (!allowed(path)) throw new PatchPolicyException("Patch file type is not allowed: " + path);
            if (operation.operation() != FileOperationType.DELETE) {
                byte[] content = operation.content().getBytes(StandardCharsets.UTF_8);
                totalBytes += content.length;
                if (SECRET.matcher(operation.content()).find()) {
                    throw new PatchPolicyException("Potential credential found in generated content: " + path);
                }
            }
        }
        if (totalBytes > properties.maxPatchBytes()) {
            throw new PatchPolicyException("Patch proposal exceeds byte limit");
        }
    }

    private boolean allowed(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private void writeAtomically(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".agentic-", ".tmp");
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new PatchPolicyException("Atomic patch write failed for " + target.getFileName(), exception);
        }
    }

    private String proposalHash(List<FileOperation> operations) {
        try {
            return sha256(mapper.writeValueAsBytes(operations));
        } catch (JsonProcessingException exception) {
            throw new PatchPolicyException("Could not hash patch proposal", exception);
        }
    }

    private byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new PatchPolicyException("Could not read patch target", exception);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    public static class PatchPolicyException extends RuntimeException {
        public PatchPolicyException(String message) { super(message); }
        public PatchPolicyException(String message, Throwable cause) { super(message, cause); }
    }
}
