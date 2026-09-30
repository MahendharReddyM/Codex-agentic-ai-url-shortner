package dev.assessment.urlshortener.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ArchitectureAgent extends AbstractAdvisoryAgent {
    @Override
    public StageType supports() {
        return StageType.ARCHITECTURE;
    }

    @Override
    public AgentOutput execute(AgentTask task) {
        return output(
                task.stage().id().equals("impact-analysis")
                        ? "Existing modules, interfaces, data flows, and regression surface mapped."
                        : "Architecture and component boundaries proposed.",
                task.stage().id() + ".md",
                "Stage uses prior outputs: " + String.join(", ", task.priorOutputs().keySet())
                        + ". Preserve ports/adapters boundaries and expose observable failure states.",
                List.of("Prefer reversible changes behind stable interfaces", "Keep domain logic framework-light"),
                List.of("In-memory adapters require replacement for multi-instance production deployment"));
    }
}

