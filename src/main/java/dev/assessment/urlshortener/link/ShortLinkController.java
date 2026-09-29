package dev.assessment.urlshortener.link;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortLinkController {
    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) {
        this.service = service;
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
    ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortLink link = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, link.destination().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}

