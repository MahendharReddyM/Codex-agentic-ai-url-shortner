package dev.assessment.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reliability")
public record ReliabilityProperties(
        int redirectsPerMinute,
        int maxAnalyticsEventsPerLink,
        String analyticsSalt,
        int analyticsWorkers,
        int analyticsQueueCapacity) {

    public ReliabilityProperties {
        if (redirectsPerMinute < 1) {
            redirectsPerMinute = 120;
        }
        if (maxAnalyticsEventsPerLink < 100) {
            maxAnalyticsEventsPerLink = 10_000;
        }
        if (analyticsSalt == null || analyticsSalt.isBlank()) {
            analyticsSalt = "local-development-only-change-me";
        }
        if (analyticsWorkers < 1 || analyticsWorkers > 32) {
            analyticsWorkers = 2;
        }
        if (analyticsQueueCapacity < 1 || analyticsQueueCapacity > 1_000_000) {
            analyticsQueueCapacity = 2_048;
        }
    }
}

