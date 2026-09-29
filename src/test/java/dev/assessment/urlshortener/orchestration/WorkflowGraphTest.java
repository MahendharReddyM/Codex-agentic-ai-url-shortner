package dev.assessment.urlshortener.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WorkflowGraphTest {

    @Test
    void rejectsCycles() {
        StageDefinition first = new StageDefinition(
                "first", "First", StageType.REQUIREMENTS, Set.of("second"), false, 1, null);
        StageDefinition second = new StageDefinition(
                "second", "Second", StageType.ARCHITECTURE, Set.of("first"), false, 1, null);

        assertThatThrownBy(() -> new WorkflowGraph(List.of(first, second), Set.of("first"), Set.of("second")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void identifiesAllTransitiveDependents() {
        WorkflowGraph graph = new WorkflowGraphFactory().create(ScenarioType.GREENFIELD);

        assertThat(graph.downstreamOf("implementation"))
                .contains("unit-tests", "integration-tests", "documentation", "quality-gate", "release-ready");
    }
}

