package dev.assessment.urlshortener.orchestration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryWorkflowRunRepository implements WorkflowRunRepository {
    private final ConcurrentMap<String, WorkflowRun> runs = new ConcurrentHashMap<>();

    @Override
    public void save(WorkflowRun run) {
        runs.put(run.id(), run);
    }

    @Override
    public Optional<WorkflowRun> findById(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public List<WorkflowRun> findAll() {
        return List.copyOf(runs.values());
    }
}

