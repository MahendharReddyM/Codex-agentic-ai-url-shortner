package dev.assessment.urlshortener.orchestration;

import java.util.List;
import java.util.Optional;

public interface WorkflowRunRepository {
    void save(WorkflowRun run);
    Optional<WorkflowRun> findById(String id);
    List<WorkflowRun> findAll();
}

