package dev.assessment.urlshortener.engineering;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EngineeringRunStore {
    void save(EngineeringRun run);
    Optional<EngineeringRun> find(UUID id);
    List<EngineeringRun> findAll();
}
