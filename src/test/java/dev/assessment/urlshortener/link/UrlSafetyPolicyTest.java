package dev.assessment.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.assessment.urlshortener.shared.DomainException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlSafetyPolicyTest {
    private final UrlSafetyPolicy policy = new UrlSafetyPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "file:///etc/passwd",
            "http://localhost:8080/secret",
            "http://10.0.0.1/internal",
            "http://192.168.1.10/internal",
            "http://[::1]/internal",
            "https://user:password@example.com"
    })
    void rejectsUnsafeDestinations(String value) {
        assertThatThrownBy(() -> policy.validate(value)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://203.0.113.10/resource",
            "https://sub.example.org/path?q=value"
    })
    void acceptsPublicHttpDestinations(String value) {
        assertThat(policy.validate(value)).hasToString(value);
    }
}

