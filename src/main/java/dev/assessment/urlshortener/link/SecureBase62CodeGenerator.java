package dev.assessment.urlshortener.link;

import java.security.SecureRandom;

import dev.assessment.urlshortener.config.UrlShortenerProperties;
import org.springframework.stereotype.Component;

@Component
public class SecureBase62CodeGenerator implements CodeGenerator {
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public SecureBase62CodeGenerator(UrlShortenerProperties properties) {
        this.length = properties.codeLength();
    }

    @Override
    public String nextCode() {
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(result);
    }
}

