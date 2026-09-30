package dev.assessment.urlshortener.engineering.model;

import java.util.regex.Pattern;

import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import org.springframework.stereotype.Component;

@Component
public class ContextSanitizer {
    private static final Pattern ASSIGNMENT_SECRET = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|token)\\s*[:=]\\s*[^\\s,;]+", Pattern.MULTILINE);
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/-]+=*");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", Pattern.DOTALL);
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?i)(ignore (all |any )?(previous|prior|system) instructions|"
                    + "system prompt|developer message|act as root|exfiltrate|reveal (the )?(secret|token|key))");

    private final int limit;

    public ContextSanitizer(AgenticExecutionProperties properties) {
        this.limit = properties.maxContextCharacters();
    }

    public String sanitize(String value) {
        String sanitized = value == null ? "" : value;
        sanitized = PRIVATE_KEY.matcher(sanitized).replaceAll("[REDACTED_PRIVATE_KEY]");
        sanitized = BEARER.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = ASSIGNMENT_SECRET.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = PROMPT_INJECTION.matcher(sanitized).replaceAll("[UNTRUSTED_INSTRUCTION_REDACTED]");
        if (sanitized.length() > limit) {
            sanitized = sanitized.substring(0, limit) + "\n[CONTEXT_TRUNCATED_BY_POLICY]";
        }
        return sanitized;
    }
}
