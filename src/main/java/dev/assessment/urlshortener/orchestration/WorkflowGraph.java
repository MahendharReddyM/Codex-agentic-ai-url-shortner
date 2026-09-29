package dev.assessment.urlshortener.orchestration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowGraph {
    private final Map<String, StageDefinition> stages;
    private final Set<String> entryGates;
    private final Set<String> exitGates;

    public WorkflowGraph(List<StageDefinition> definitions, Set<String> entryGates, Set<String> exitGates) {
        Map<String, StageDefinition> indexed = new LinkedHashMap<>();
        definitions.forEach(stage -> {
            if (indexed.putIfAbsent(stage.id(), stage) != null) {
                throw new IllegalArgumentException("Duplicate stage id: " + stage.id());
            }
        });
        this.stages = Map.copyOf(indexed);
        this.entryGates = Set.copyOf(entryGates);
        this.exitGates = Set.copyOf(exitGates);
        validate();
    }

    public Map<String, StageDefinition> stages() {
        return stages;
    }

    public Set<String> entryGates() {
        return entryGates;
    }

    public Set<String> exitGates() {
        return exitGates;
    }

    public Set<String> downstreamOf(String stageId) {
        Set<String> result = new java.util.LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(stageId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            stages.values().stream()
                    .filter(stage -> stage.dependencies().contains(current))
                    .map(StageDefinition::id)
                    .filter(result::add)
                    .forEach(queue::addLast);
        }
        return result;
    }

    private void validate() {
        if (stages.isEmpty() || entryGates.isEmpty() || exitGates.isEmpty()) {
            throw new IllegalArgumentException("Graph requires stages plus entry and exit gates");
        }
        if (!stages.keySet().containsAll(entryGates) || !stages.keySet().containsAll(exitGates)) {
            throw new IllegalArgumentException("Entry and exit gates must reference existing stages");
        }
        for (StageDefinition stage : stages.values()) {
            if (!stages.keySet().containsAll(stage.dependencies())) {
                throw new IllegalArgumentException("Unknown dependency for stage: " + stage.id());
            }
            if (stage.fallbackStageId() != null && !stages.containsKey(stage.fallbackStageId())) {
                throw new IllegalArgumentException("Unknown fallback for stage: " + stage.id());
            }
        }
        assertAcyclic();
    }

    private void assertAcyclic() {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        stages.forEach((id, stage) -> inDegree.put(id, stage.dependencies().size()));
        ArrayDeque<String> ready = new ArrayDeque<>();
        inDegree.forEach((id, degree) -> {
            if (degree == 0) ready.add(id);
        });
        List<String> visited = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.removeFirst();
            visited.add(current);
            stages.values().stream()
                    .filter(stage -> stage.dependencies().contains(current))
                    .forEach(stage -> {
                        int remaining = inDegree.compute(stage.id(), (id, degree) -> degree - 1);
                        if (remaining == 0) ready.addLast(stage.id());
                    });
        }
        if (visited.size() != stages.size()) {
            throw new IllegalArgumentException("Workflow graph contains a cycle");
        }
    }
}

