package dev.assessment.urlshortener.orchestration;

import java.util.List;

public record AuditTrailView(String runId, boolean hashChainValid, List<RunEvent> events) {
}

