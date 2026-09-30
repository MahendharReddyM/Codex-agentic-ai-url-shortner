package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class SecurityReviewAgent extends AbstractAdvisoryAgent {
    @Override
    public StageType supports() {
        return StageType.SECURITY_REVIEW;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        return output(
                "Threat and policy review completed; high-impact execution remains human-gated.",
                "security-review.md",
                "Validate input, reject local/private destinations, avoid raw client identifiers, "
                        + "bound retries, and never permit autonomous production deployment.",
                List.of("Human approval is mandatory before implementation and release"),
                List.of("DNS rebinding requires an egress proxy or resolution-time enforcement in production"));
    }
}

