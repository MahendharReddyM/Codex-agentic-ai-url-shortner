package dev.assessment.urlshortener.analytics;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.assessment.urlshortener.config.ReliabilityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final AnalyticsRepository repository;
    private final PrivacyHasher privacyHasher;
    private final Clock clock;
    private final ThreadPoolExecutor writer;
    private final Counter acceptedEvents;
    private final Counter writtenEvents;
    private final Counter droppedEvents;

    public AnalyticsService(
            AnalyticsRepository repository,
            PrivacyHasher privacyHasher,
            Clock clock,
            MeterRegistry meterRegistry,
            ReliabilityProperties properties) {
        this.repository = repository;
        this.privacyHasher = privacyHasher;
        this.clock = clock;
        this.writer = new ThreadPoolExecutor(
                properties.analyticsWorkers(),
                properties.analyticsWorkers(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.analyticsQueueCapacity()),
                Thread.ofVirtual().name("analytics-writer-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.acceptedEvents = meterRegistry.counter("shortener.analytics.accepted");
        this.writtenEvents = meterRegistry.counter("shortener.analytics.written");
        this.droppedEvents = meterRegistry.counter("shortener.analytics.dropped");
        Gauge.builder("shortener.analytics.queue.depth", writer, executor -> executor.getQueue().size())
                .description("Click events waiting for asynchronous persistence")
                .register(meterRegistry);
    }

    public void record(String code, String remoteAddress, String referrer, String userAgent) {
        ClickEvent event = new ClickEvent(
                code,
                Instant.now(clock),
                privacyHasher.dailyAnonymousId(remoteAddress == null ? "unknown" : remoteAddress),
                referrerDomain(referrer),
                clientFamily(userAgent));
        try {
            writer.execute(() -> append(event));
            acceptedEvents.increment();
        } catch (RejectedExecutionException exception) {
            // Backpressure is explicit and bounded: redirects remain available while telemetry records loss.
            droppedEvents.increment();
        }
    }

    private void append(ClickEvent event) {
        try {
            repository.append(event);
            writtenEvents.increment();
        } catch (RuntimeException exception) {
            droppedEvents.increment();
        }
    }

    @PreDestroy
    void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(2, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public LinkAnalyticsResponse summarize(String code) {
        List<ClickEvent> events = repository.findByCode(code);
        Instant last = events.stream().map(ClickEvent::occurredAt).max(Comparator.naturalOrder()).orElse(null);
        Map<LocalDate, Long> byDay = events.stream().collect(Collectors.groupingBy(
                event -> event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(),
                LinkedHashMap::new,
                Collectors.counting()));
        return new LinkAnalyticsResponse(
                code,
                events.size(),
                last,
                byDay,
                topCounts(events, ClickEvent::referrerDomain),
                topCounts(events, ClickEvent::clientFamily));
    }

    private Map<String, Long> topCounts(List<ClickEvent> events, Function<ClickEvent, String> classifier) {
        return events.stream().map(classifier).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String referrerDomain(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return "direct";
        }
        try {
            String host = new URI(referrer).getHost();
            return host == null ? "invalid" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            return "invalid";
        }
    }

    private String clientFamily(String userAgent) {
        if (userAgent == null) {
            return "unknown";
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("bot") || normalized.contains("crawler") || normalized.contains("spider")) {
            return "bot";
        }
        if (normalized.contains("mobile") || normalized.contains("android") || normalized.contains("iphone")) {
            return "mobile";
        }
        return "desktop-or-service";
    }
}

