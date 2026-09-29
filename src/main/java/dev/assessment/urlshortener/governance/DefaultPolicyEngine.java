package dev.assessment.urlshortener.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class DefaultPolicyEngine implements PolicyEngine {

    @Override
    public PolicyDecision evaluate(String requirement) {
        String normalized = requirement.toLowerCase(Locale.ROOT);
        List<PolicyFinding> findings = new ArrayList<>();
        if (containsAny(normalized, "password=", "api_key=", "api-key=", "secret=")) {
            findings.add(new PolicyFinding(PolicySeverity.BLOCKING, "POSSIBLE_SECRET",
                    "Requirement appears to contain a credential; remove it before execution."));
        }
        if (containsAny(normalized, "bypass approval", "disable security", "skip all tests")) {
            findings.add(new PolicyFinding(PolicySeverity.BLOCKING, "CONTROL_BYPASS",
                    "Requested work attempts to bypass a mandatory governance control."));
        }
        if (containsAny(normalized, "drop database", "delete all data", "irreversible migration")) {
            findings.add(new PolicyFinding(PolicySeverity.BLOCKING, "DESTRUCTIVE_CHANGE",
                    "Destructive or irreversible data actions require an external change process."));
        }
        if (containsAny(normalized, "deploy to production", "production deployment", "release to production")) {
            findings.add(new PolicyFinding(PolicySeverity.REQUIRES_APPROVAL, "PRODUCTION_CHANGE",
                    "Production promotion is outside agent authority and requires owner approval."));
        }
        if (containsAny(normalized, "whatever is best", "and so on", "etc.")) {
            findings.add(new PolicyFinding(PolicySeverity.WARNING, "AMBIGUOUS_SCOPE",
                    "Open-ended language should be clarified before implementation."));
        }
        if (findings.isEmpty()) {
            findings.add(new PolicyFinding(PolicySeverity.INFO, "BASELINE_CONTROLS",
                    "Baseline security, testing, approval, and change controls apply."));
        }
        boolean allowed = findings.stream().noneMatch(finding -> finding.severity() == PolicySeverity.BLOCKING);
        return new PolicyDecision(allowed, findings);
    }

    private boolean containsAny(String source, String... values) {
        for (String value : values) {
            if (source.contains(value)) return true;
        }
        return false;
    }
}

