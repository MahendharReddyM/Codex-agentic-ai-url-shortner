package dev.assessment.urlshortener.link;

import java.net.URI;

import dev.assessment.urlshortener.analytics.AnalyticsService;
import dev.assessment.urlshortener.analytics.PrivacyHasher;
import dev.assessment.urlshortener.reliability.RedirectRateLimiter;
import dev.assessment.urlshortener.shared.DomainException;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortLinkController {
    private final ShortLinkService service;
    private final AnalyticsService analyticsService;
    private final PrivacyHasher privacyHasher;
    private final RedirectRateLimiter rateLimiter;

    public ShortLinkController(
            ShortLinkService service,
            AnalyticsService analyticsService,
            PrivacyHasher privacyHasher,
            RedirectRateLimiter rateLimiter) {
        this.service = service;
        this.analyticsService = analyticsService;
        this.privacyHasher = privacyHasher;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/v1/links")
    ResponseEntity<ShortLinkResponse> create(
            @Valid @RequestBody CreateShortLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ShortLinkResponse response = service.create(request, idempotencyKey);
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    @GetMapping("/api/v1/links/{code}")
    ShortLinkResponse get(@PathVariable String code) {
        return service.get(code);
    }

    @DeleteMapping("/api/v1/links/{code}")
    ResponseEntity<Void> deactivate(@PathVariable String code) {
        service.deactivate(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/r/{code}")
    ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        String clientKey = privacyHasher.dailyAnonymousId(request.getRemoteAddr());
        if (!rateLimiter.allow(clientKey)) {
            throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Redirect rate limit exceeded; retry in the next minute");
        }
        ShortLink link = service.resolve(code);
        analyticsService.record(
                code,
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.REFERER),
                request.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, link.destination().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
