package dev.assessment.urlshortener.engineering;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.assessment.urlshortener.engineering.config.AgenticExecutionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.agentic", name = "persistence", havingValue = "postgresql")
public class PostgresEngineeringRunStore implements EngineeringRunStore {
    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS engineering_runs (
              id UUID PRIMARY KEY,
              payload TEXT NOT NULL,
              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;
    private static final String UPSERT = """
            INSERT INTO engineering_runs (id, payload, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, updated_at = CURRENT_TIMESTAMP
            """;

    private final AgenticExecutionProperties properties;
    private final ObjectMapper mapper;

    public PostgresEngineeringRunStore(ObjectMapper applicationMapper, AgenticExecutionProperties properties) {
        this.properties = properties;
        this.mapper = applicationMapper.copy()
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
        initializeSchema();
    }

    @Override
    public void save(EngineeringRun run) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setObject(1, run.id());
            statement.setString(2, mapper.writeValueAsString(run));
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist engineering run " + run.id(), exception);
        }
    }

    @Override
    public Optional<EngineeringRun> find(UUID id) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT payload FROM engineering_runs WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapper.readValue(result.getString(1), EngineeringRun.class))
                        : Optional.empty();
            }
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("Could not load engineering run " + id, exception);
        }
    }

    @Override
    public List<EngineeringRun> findAll() {
        List<EngineeringRun> runs = new ArrayList<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT payload FROM engineering_runs ORDER BY updated_at DESC")) {
            while (result.next()) runs.add(mapper.readValue(result.getString(1), EngineeringRun.class));
            return List.copyOf(runs);
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("Could not list persisted engineering runs", exception);
        }
    }

    private void initializeSchema() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize PostgreSQL engineering-run storage", exception);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(properties.jdbcUrl(), properties.jdbcUser(), properties.jdbcPassword());
    }
}
