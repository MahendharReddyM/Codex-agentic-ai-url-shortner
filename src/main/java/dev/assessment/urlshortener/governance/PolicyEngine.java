package dev.assessment.urlshortener.governance;

public interface PolicyEngine {
    PolicyDecision evaluate(String requirement);
}

