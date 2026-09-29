package dev.assessment.urlshortener.link;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

import dev.assessment.urlshortener.config.UrlShortenerProperties;
import dev.assessment.urlshortener.shared.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ShortLinkService {
    private static final int MAX_CODE_ATTEMPTS = 10;
    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api", "r", "actuator", "swagger-ui", "v3", "health", "metrics");

    private final ShortLinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final UrlSafetyPolicy urlSafetyPolicy;
    private final UrlShortenerProperties properties;
    private final Clock clock;

    public ShortLinkService(
            ShortLinkRepository repository,
            CodeGenerator codeGenerator,
            UrlSafetyPolicy urlSafetyPolicy,
            UrlShortenerProperties properties,
            Clock clock) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.urlSafetyPolicy = urlSafetyPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    public ShortLinkResponse create(CreateShortLinkRequest request, String idempotencyKey) {
        URI destination = urlSafetyPolicy.validate(request.url());
        Instant now = Instant.now(clock);
        validateExpiry(request.expiresAt(), now);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String fingerprint = fingerprint(destination, request.customAlias(), request.expiresAt());

        ShortLink existing = repository.findByIdempotencyKey(normalizedKey).orElse(null);
        if (existing != null) {
            if (!fingerprint.equals(existing.requestFingerprint())) {
                throw new DomainException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used for a different request");
            }
            return toResponse(existing);
        }

        String alias = request.customAlias();
        if (alias != null) {
            validateAlias(alias);
            return createWithCode(alias, destination, normalizedKey, fingerprint, now, request.expiresAt());
        }

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.nextCode();
            ShortLink link = new ShortLink(candidate, destination, normalizedKey, fingerprint, now, request.expiresAt());
            if (repository.createIfCodeAvailable(link)) {
                return toResponse(link);
            }
            existing = repository.findByIdempotencyKey(normalizedKey).orElse(null);
            if (existing != null && fingerprint.equals(existing.requestFingerprint())) {
                return toResponse(existing);
            }
        }
        throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "CODE_SPACE_EXHAUSTED",
                "Could not allocate a unique short code; retry later");
    }

    public ShortLink resolve(String code) {
        ShortLink link = find(code);
        if (!link.isAvailableAt(Instant.now(clock))) {
            throw new DomainException(HttpStatus.GONE, "LINK_UNAVAILABLE", "Short link is expired or disabled");
        }
        return link;
    }

    public ShortLinkResponse get(String code) {
        return toResponse(find(code));
    }

    public void deactivate(String code) {
        ShortLink link = find(code);
        link.deactivate();
        repository.save(link);
    }

    private ShortLinkResponse createWithCode(
            String code,
            URI destination,
            String idempotencyKey,
            String fingerprint,
            Instant createdAt,
            Instant expiresAt) {
        ShortLink link = new ShortLink(code, destination, idempotencyKey, fingerprint, createdAt, expiresAt);
        if (!repository.createIfCodeAvailable(link)) {
            throw new DomainException(HttpStatus.CONFLICT, "ALIAS_UNAVAILABLE", "Custom alias is already in use");
        }
        return toResponse(link);
    }

    private ShortLink find(String code) {
        return repository.findByCode(code).orElseThrow(() ->
                new DomainException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "Short link was not found"));
    }

    private void validateExpiry(Instant expiry, Instant now) {
        if (expiry != null && !expiry.isAfter(now)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY", "expiresAt must be in the future");
        }
    }

    private void validateAlias(String alias) {
        if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "RESERVED_ALIAS", "Custom alias is reserved");
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.length() > 128) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key cannot exceed 128 characters");
        }
        return normalized;
    }

    private String fingerprint(URI destination, String alias, Instant expiresAt) {
        String material = destination + "|" + alias + "|" + expiresAt;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private ShortLinkResponse toResponse(ShortLink link) {
        return new ShortLinkResponse(
                link.code(),
                properties.publicBaseUrl() + "/r/" + link.code(),
                link.destination().toString(),
                link.createdAt(),
                link.expiresAt(),
                link.active());
    }
}

