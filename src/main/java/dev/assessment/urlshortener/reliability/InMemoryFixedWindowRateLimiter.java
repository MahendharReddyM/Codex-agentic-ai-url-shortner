package dev.assessment.urlshortener.reliability;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import dev.assessment.urlshortener.config.ReliabilityProperties;
import org.springframework.stereotype.Component;

@Component
public class InMemoryFixedWindowRateLimiter implements RedirectRateLimiter {
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limit;

    public InMemoryFixedWindowRateLimiter(Clock clock, ReliabilityProperties properties) {
        this.clock = clock;
        this.limit = properties.redirectsPerMinute();
    }

    @Override
    public boolean allow(String clientKey) {
        Instant minute = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES);
        Window window = windows.compute(clientKey, (key, current) -> {
            if (current == null || !current.minute().equals(minute)) {
                return new Window(minute, new AtomicInteger(1));
            }
            current.requests().incrementAndGet();
            return current;
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute().isBefore(minute));
        }
        return window.requests().get() <= limit;
    }

    private record Window(Instant minute, AtomicInteger requests) {
    }
}

