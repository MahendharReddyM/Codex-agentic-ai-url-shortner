package dev.assessment.urlshortener.orchestration;

public enum StageStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_APPROVAL,
    SUCCEEDED,
    FAILED,
    INVALIDATED,
    ROLLED_BACK,
    SKIPPED
}

