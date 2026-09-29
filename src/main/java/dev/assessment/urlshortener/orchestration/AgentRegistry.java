package dev.assessment.urlshortener.orchestration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class AgentRegistry {
    private final Map<StageType, StageAgent> agents = new EnumMap<>(StageType.class);

    public AgentRegistry(List<StageAgent> registeredAgents) {
        registeredAgents.forEach(agent -> {
            if (agents.putIfAbsent(agent.supports(), agent) != null) {
                throw new IllegalStateException("Multiple agents registered for " + agent.supports());
            }
        });
    }

    public StageAgent forStage(StageType type) {
        StageAgent agent = agents.get(type);
        if (agent == null) {
            throw new IllegalStateException("No agent registered for " + type);
        }
        return agent;
    }
}

