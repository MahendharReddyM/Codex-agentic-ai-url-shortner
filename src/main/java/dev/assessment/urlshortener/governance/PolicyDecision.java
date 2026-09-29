package dev.assessment.urlshortener.governance;

import java.util.List;

public record PolicyDecision(boolean allowed, List<PolicyFinding> findings) {
    public PolicyDecision {
        findings = List.copyOf(findings);
    }
}

