package dev.assessment.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record UrlShortenerProperties(String publicBaseUrl, int codeLength) {

    public UrlShortenerProperties {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "http://localhost:8080";
        }
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        if (codeLength < 6 || codeLength > 16) {
            codeLength = 8;
        }
    }
}

