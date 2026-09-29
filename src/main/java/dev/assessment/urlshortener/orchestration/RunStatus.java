package dev.assessment.urlshortener.orchestration;

public enum RunStatus {
    PLANNING,
    RUNNING,
    WAITING_FOR_APPROVAL,
    SUCCEEDED,
    FAILED,
    SAFE_STOPPED,
    CANCELLED,
    ROLLED_BACK
}

