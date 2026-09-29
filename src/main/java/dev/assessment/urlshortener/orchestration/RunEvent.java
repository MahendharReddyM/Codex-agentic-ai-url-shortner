package dev.assessment.urlshortener.orchestration;

import java.time.Instant;
import java.util.Map;

public record RunEvent(
        long sequence,
        Instant occurredAt,
        String type,
        String stageId,
        String actor,
        String message,
        Map<String, String> details) {
}

