package dev.assessment.urlshortener.orchestration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

@Component
public class AuditTrailVerifier {
    public boolean isStructurallyValid(List<RunEvent> events) {
        String expectedPrevious = "GENESIS";
        long expectedSequence = 1;
        for (RunEvent event : events) {
            if (event.sequence() != expectedSequence || !expectedPrevious.equals(event.previousHash())
                    || !expectedHash(event).equals(event.eventHash())) {
                return false;
            }
            expectedSequence++;
            expectedPrevious = event.eventHash();
        }
        return true;
    }

    private String expectedHash(RunEvent event) {
        String canonical = event.sequence() + "|" + event.occurredAt() + "|" + event.type() + "|"
                + event.stageId() + "|" + event.actor() + "|" + event.message() + "|"
                + new TreeMap<>(event.details()) + "|" + event.previousHash();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
