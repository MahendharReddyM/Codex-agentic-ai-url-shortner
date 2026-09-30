package dev.assessment.urlshortener.engineering;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.agentic", name = "persistence", havingValue = "file", matchIfMissing = true)
public class FileEngineeringRunStore implements EngineeringRunStore {
    private final Path root;
    private final ObjectMapper mapper;
    private final ConcurrentMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public FileEngineeringRunStore(ObjectMapper applicationMapper, AgenticExecutionProperties properties) {
        this.root = properties.persistenceRoot().toAbsolutePath().normalize();
        this.mapper = applicationMapper.copy()
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        createRoot();
    }

    @Override
    public void save(EngineeringRun run) {
        synchronized (locks.computeIfAbsent(run.id(), ignored -> new Object())) {
            Path target = path(run.id());
            try {
                Path temporary = Files.createTempFile(root, run.id().toString(), ".tmp");
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), run);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not persist engineering run " + run.id(), exception);
            }
        }
    }

    @Override
    public Optional<EngineeringRun> find(UUID id) {
        Path target = path(id);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        synchronized (locks.computeIfAbsent(id, ignored -> new Object())) {
            try {
                return Optional.of(mapper.readValue(target.toFile(), EngineeringRun.class));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load engineering run " + id, exception);
            }
        }
    }

    @Override
    public List<EngineeringRun> findAll() {
        List<EngineeringRun> runs = new ArrayList<>();
        try (var files = Files.list(root)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> parse(path).ifPresent(runs::add));
            return List.copyOf(runs);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list persisted engineering runs", exception);
        }
    }

    private Optional<EngineeringRun> parse(Path path) {
        try {
            return Optional.of(mapper.readValue(path.toFile(), EngineeringRun.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load persisted engineering run " + path.getFileName(), exception);
        }
    }

    private Path path(UUID id) {
        return root.resolve(id + ".json");
    }

    private void createRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create engineering persistence directory", exception);
        }
    }
}
