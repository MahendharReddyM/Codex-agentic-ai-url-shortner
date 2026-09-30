package dev.assessment.urlshortener.reliability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import dev.assessment.urlshortener.config.ReliabilityProperties;
import org.junit.jupiter.api.Test;

class InMemoryFixedWindowRateLimiterTest {

    @Test
    void boundsRequestsAndResetsInNextWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryFixedWindowRateLimiter limiter = new InMemoryFixedWindowRateLimiter(
                clock, new ReliabilityProperties(2, 100, "test-secret", 2, 2_048));

        assertThat(limiter.allow("client")).isTrue();
        assertThat(limiter.allow("client")).isTrue();
        assertThat(limiter.allow("client")).isFalse();

        clock.instant = clock.instant.plusSeconds(60);
        assertThat(limiter.allow("client")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}

