package dev.assessment.urlshortener.orchestration;

import java.time.Instant;

public final class StageExecution {
    private StageStatus status = StageStatus.PENDING;
    private int attempts;
    private Instant startedAt;
    private Instant completedAt;
    private String error;
    private AgentOutput output;

    public StageStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public String error() { return error; }
    public AgentOutput output() { return output; }

    void waitingForApproval() { status = StageStatus.WAITING_FOR_APPROVAL; }

    void start(Instant now) {
        status = StageStatus.RUNNING;
        attempts++;
        startedAt = now;
        error = null;
    }

    void succeed(AgentOutput result, Instant now) {
        status = StageStatus.SUCCEEDED;
        output = result;
        completedAt = now;
        error = null;
    }

    void fail(String reason, Instant now) {
        status = StageStatus.FAILED;
        error = reason;
        completedAt = now;
    }

    void resetForRetry() { status = StageStatus.PENDING; }

    void invalidate() {
        status = StageStatus.INVALIDATED;
        attempts = 0;
        startedAt = null;
        output = null;
        completedAt = null;
        error = null;
    }

    void resetInvalidated() {
        if (status == StageStatus.INVALIDATED) status = StageStatus.PENDING;
    }

    void rollBack(Instant now) {
        status = StageStatus.ROLLED_BACK;
        completedAt = now;
        output = null;
    }

    void skip(Instant now) {
        status = StageStatus.SKIPPED;
        completedAt = now;
    }
}
