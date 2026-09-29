package dev.assessment.urlshortener.analytics;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import dev.assessment.urlshortener.config.ReliabilityProperties;
import org.springframework.stereotype.Component;

@Component
public class PrivacyHasher {
    private final byte[] secret;
    private final Clock clock;

    public PrivacyHasher(ReliabilityProperties properties, Clock clock) {
        this.secret = properties.analyticsSalt().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String dailyAnonymousId(String value) {
        String material = LocalDate.now(clock) + ":" + value;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)), 0, 12);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 must be available", exception);
        }
    }
}

