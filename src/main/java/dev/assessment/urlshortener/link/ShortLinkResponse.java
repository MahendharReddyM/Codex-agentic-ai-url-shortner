package dev.assessment.urlshortener.link;

import java.time.Instant;

public record ShortLinkResponse(
        String code,
        String shortUrl,
        String destination,
        Instant createdAt,
        Instant expiresAt,
        boolean active) {
}

