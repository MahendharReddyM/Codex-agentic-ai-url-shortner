package dev.assessment.urlshortener.analytics;

import java.time.Instant;

public record ClickEvent(
        String code,
        Instant occurredAt,
        String anonymousClientId,
        String referrerDomain,
        String clientFamily) {
}

