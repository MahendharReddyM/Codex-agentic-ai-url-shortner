package dev.assessment.urlshortener.link;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

import dev.assessment.urlshortener.shared.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyPolicy {
    private static final Set<String> LOCAL_NAMES = Set.of("localhost", "localhost.localdomain");

    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > 2048) {
            throw invalid("URL must contain between 1 and 2048 characters");
        }
        try {
            URI uri = new URI(rawUrl).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw invalid("Only http and https URLs are allowed");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw invalid("URL must include a valid host");
            }
            if (uri.getUserInfo() != null) {
                throw invalid("URLs containing embedded credentials are not allowed");
            }
            rejectLocalOrPrivateLiteral(uri.getHost());
            return uri;
        } catch (URISyntaxException exception) {
            throw invalid("URL syntax is invalid");
        }
    }

    private void rejectLocalOrPrivateLiteral(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (LOCAL_NAMES.contains(normalized) || normalized.endsWith(".localhost")) {
            throw invalid("Local destinations are not allowed");
        }
        if (!looksLikeIpLiteral(normalized)) {
            return;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw invalid("Private or local IP destinations are not allowed");
            }
        } catch (UnknownHostException exception) {
            throw invalid("IP destination is invalid");
        }
    }

    private boolean looksLikeIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    private DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "UNSAFE_OR_INVALID_URL", message);
    }
}

