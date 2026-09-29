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
import java.util.function.Function;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final AnalyticsRepository repository;
    private final PrivacyHasher privacyHasher;
    private final Clock clock;
    private final Counter droppedEvents;

    public AnalyticsService(
            AnalyticsRepository repository,
            PrivacyHasher privacyHasher,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.privacyHasher = privacyHasher;
        this.clock = clock;
        this.droppedEvents = meterRegistry.counter("shortener.analytics.dropped");
    }

    public void record(String code, String remoteAddress, String referrer, String userAgent) {
        try {
            repository.append(new ClickEvent(
                    code,
                    Instant.now(clock),
                    privacyHasher.dailyAnonymousId(remoteAddress == null ? "unknown" : remoteAddress),
                    referrerDomain(referrer),
                    clientFamily(userAgent)));
        } catch (RuntimeException exception) {
            // Redirect availability takes precedence over non-critical analytics.
            droppedEvents.increment();
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

