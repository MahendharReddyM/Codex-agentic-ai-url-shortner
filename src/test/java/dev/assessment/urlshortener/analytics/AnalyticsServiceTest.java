package dev.assessment.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.assessment.urlshortener.config.ReliabilityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    @Test
    void aggregatesWithoutRetainingRawClientAddress() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        ReliabilityProperties properties = new ReliabilityProperties(10, 100, "test-secret", 2, 2_048);
        InMemoryAnalyticsRepository repository = new InMemoryAnalyticsRepository(properties);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsService service = new AnalyticsService(
                repository, new PrivacyHasher(properties, clock), clock, meterRegistry, properties);

        try {
            service.record("abc123", "198.51.100.7", "https://news.example/article", "Mozilla Mobile");
            service.record("abc123", "198.51.100.7", null, "SearchBot/1.0");

            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                LinkAnalyticsResponse response = service.summarize("abc123");
                assertThat(response.totalClicks()).isEqualTo(2);
                assertThat(response.topReferrers()).containsEntry("news.example", 1L).containsEntry("direct", 1L);
                assertThat(response.clientFamilies()).containsEntry("mobile", 1L).containsEntry("bot", 1L);
            });
            assertThat(repository.findByCode("abc123"))
                    .allSatisfy(event -> assertThat(event.anonymousClientId()).doesNotContain("198.51.100.7"));
            assertThat(meterRegistry.counter("shortener.analytics.written").count()).isEqualTo(2);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void appliesBoundedBackpressureWithoutBlockingTheRedirectPath() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        ReliabilityProperties properties = new ReliabilityProperties(10, 100, "test-secret", 1, 1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        AnalyticsRepository slowRepository = new AnalyticsRepository() {
            @Override
            public void append(ClickEvent event) {
                writerStarted.countDown();
                try {
                    releaseWriter.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public List<ClickEvent> findByCode(String code) {
                return List.of();
            }
        };
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AnalyticsService service = new AnalyticsService(
                slowRepository, new PrivacyHasher(properties, clock), clock, meterRegistry, properties);

        try {
            service.record("one", "198.51.100.1", null, null);
            assertThat(writerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            service.record("two", "198.51.100.2", null, null);

            long startedAt = System.nanoTime();
            service.record("three", "198.51.100.3", null, null);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(elapsed).isLessThan(Duration.ofMillis(250));
            assertThat(meterRegistry.counter("shortener.analytics.dropped").count()).isEqualTo(1);
            assertThat(meterRegistry.find("shortener.analytics.queue.depth").gauge().value()).isEqualTo(1);
        } finally {
            releaseWriter.countDown();
            service.shutdown();
        }
    }
}

