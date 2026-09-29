package dev.assessment.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.assessment.urlshortener.config.UrlShortenerProperties;
import dev.assessment.urlshortener.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShortLinkServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        service = new ShortLinkService(
                new InMemoryShortLinkRepository(),
                () -> "Ab3deF90",
                new UrlSafetyPolicy(),
                new UrlShortenerProperties("https://sho.rt/", 8),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAndResolvesSafeUrl() {
        ShortLinkResponse created = service.create(
                new CreateShortLinkRequest("https://example.com/a/../article?campaign=one", null, NOW.plusSeconds(60)),
                "request-1");

        assertThat(created.code()).isEqualTo("Ab3deF90");
        assertThat(created.shortUrl()).isEqualTo("https://sho.rt/r/Ab3deF90");
        assertThat(service.resolve(created.code()).destination().toString())
                .isEqualTo("https://example.com/article?campaign=one");
    }

    @Test
    void returnsSameResourceForIdempotentReplay() {
        CreateShortLinkRequest request = new CreateShortLinkRequest("https://example.com", null, null);

        ShortLinkResponse first = service.create(request, "same-key");
        ShortLinkResponse replay = service.create(request, "same-key");

        assertThat(replay).isEqualTo(first);
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentPayload() {
        service.create(new CreateShortLinkRequest("https://example.com/one", null, null), "same-key");

        assertThatThrownBy(() -> service.create(
                new CreateShortLinkRequest("https://example.com/two", null, null), "same-key"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("different request");
    }

    @Test
    void rejectsPrivateDestinationsAndEmbeddedCredentials() {
        assertThatThrownBy(() -> service.create(
                new CreateShortLinkRequest("http://127.0.0.1/admin", null, null), null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.create(
                new CreateShortLinkRequest("https://user:pass@example.com", null, null), null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void expiresAndDeactivatesLinks() {
        ShortLinkResponse created = service.create(
                new CreateShortLinkRequest("https://example.com", "campaign-one", NOW.plusSeconds(1)), null);
        service.deactivate(created.code());

        assertThatThrownBy(() -> service.resolve(created.code()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("expired or disabled");
    }
}

