package dev.assessment.urlshortener.reliability;

public interface RedirectRateLimiter {
    boolean allow(String clientKey);
}

