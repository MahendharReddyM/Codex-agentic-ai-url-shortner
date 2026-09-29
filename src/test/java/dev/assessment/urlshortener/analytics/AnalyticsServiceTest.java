package dev.assessment.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.assessment.urlshortener.config.ReliabilityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    @Test
    void aggregatesWithoutRetainingRawClientAddress() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        ReliabilityProperties properties = new ReliabilityProperties(10, 100, "test-secret");
        InMemoryAnalyticsRepository repository = new InMemoryAnalyticsRepository(properties);
        AnalyticsService service = new AnalyticsService(
                repository, new PrivacyHasher(properties, clock), clock, new SimpleMeterRegistry());

        service.record("abc123", "198.51.100.7", "https://news.example/article", "Mozilla Mobile");
        service.record("abc123", "198.51.100.7", null, "SearchBot/1.0");

        LinkAnalyticsResponse response = service.summarize("abc123");
        assertThat(response.totalClicks()).isEqualTo(2);
        assertThat(response.topReferrers()).containsEntry("news.example", 1L).containsEntry("direct", 1L);
        assertThat(response.clientFamilies()).containsEntry("mobile", 1L).containsEntry("bot", 1L);
        assertThat(repository.findByCode("abc123"))
                .allSatisfy(event -> assertThat(event.anonymousClientId()).doesNotContain("198.51.100.7"));
    }
}

