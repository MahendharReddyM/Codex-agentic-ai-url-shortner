package dev.assessment.urlshortener.link;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public final class ShortLink {
    private final String code;
    private final URI destination;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final Instant createdAt;
    private final Instant expiresAt;
    private volatile boolean active;

    public ShortLink(
            String code,
            URI destination,
            String idempotencyKey,
            String requestFingerprint,
            Instant createdAt,
            Instant expiresAt) {
        this.code = Objects.requireNonNull(code);
        this.destination = Objects.requireNonNull(destination);
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = expiresAt;
        this.active = true;
    }

    public String code() {
        return code;
    }

    public URI destination() {
        return destination;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isAvailableAt(Instant instant) {
        return active && (expiresAt == null || expiresAt.isAfter(instant));
    }

    public boolean active() {
        return active;
    }

    public void deactivate() {
        active = false;
    }
}

