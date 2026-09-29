package dev.assessment.urlshortener.orchestration;

public record OrchestrationMetrics(
        long totalRuns,
        long activeRuns,
        long completedRuns,
        double successRate,
        long retryCount,
        double retryFrequency,
        long rollbackCount,
        double rollbackFrequency,
        double meanTimeToRecoveryMillis,
        double averageEndToEndLatencyMillis) {
}

