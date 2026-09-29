package dev.assessment.urlshortener.orchestration;

import java.util.Set;

public record StageDefinition(
        String id,
        String name,
        StageType type,
        Set<String> dependencies,
        boolean approvalRequired,
        int maxAttempts,
        String fallbackStageId) {

    public StageDefinition {
        dependencies = Set.copyOf(dependencies);
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 5");
        }
    }
}

