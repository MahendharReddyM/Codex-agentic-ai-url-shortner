package dev.assessment.urlshortener.link;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateShortLinkRequest(
        @NotBlank @Size(max = 2048) String url,
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$", message = "must be 4-32 URL-safe characters")
        String customAlias,
        Instant expiresAt) {
}

