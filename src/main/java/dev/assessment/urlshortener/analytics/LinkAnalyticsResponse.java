package dev.assessment.urlshortener.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record LinkAnalyticsResponse(
        String code,
        long totalClicks,
        Instant lastClickedAt,
        Map<LocalDate, Long> clicksByDay,
        Map<String, Long> topReferrers,
        Map<String, Long> clientFamilies) {
}

