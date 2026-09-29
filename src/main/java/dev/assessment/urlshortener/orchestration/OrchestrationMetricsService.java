package dev.assessment.urlshortener.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class OrchestrationMetricsService {
    private final WorkflowRunRepository repository;

    public OrchestrationMetricsService(WorkflowRunRepository repository) {
        this.repository = repository;
    }

    public OrchestrationMetrics snapshot() {
        List<WorkflowRun> runs = repository.findAll();
        long completed = runs.stream().filter(run -> run.completedAt() != null).count();
        long succeeded = runs.stream().filter(run -> run.status() == RunStatus.SUCCEEDED).count();
        long retries = countEvents(runs, "STAGE_RETRY");
        long rollbacks = countEvents(runs, "RUN_ROLLED_BACK") + countEvents(runs, "STAGE_ROLLED_BACK");
        double latency = runs.stream().filter(run -> run.completedAt() != null)
                .mapToLong(run -> Duration.between(run.createdAt(), run.completedAt()).toMillis())
                .average().orElse(0);
        return new OrchestrationMetrics(
                runs.size(),
                runs.size() - completed,
                completed,
                ratio(succeeded, completed),
                retries,
                ratio(retries, runs.size()),
                rollbacks,
                ratio(rollbacks, runs.size()),
                meanTimeToRecovery(runs),
                latency);
    }

    private long countEvents(List<WorkflowRun> runs, String type) {
        return runs.stream().flatMap(run -> run.events().stream()).filter(event -> event.type().equals(type)).count();
    }

    private double meanTimeToRecovery(List<WorkflowRun> runs) {
        long totalMillis = 0;
        long recoveries = 0;
        for (WorkflowRun run : runs) {
            Map<String, Instant> failures = new HashMap<>();
            for (RunEvent event : run.events()) {
                if (event.stageId() == null) continue;
                if (event.type().equals("STAGE_FAILED")) {
                    failures.put(event.stageId(), event.occurredAt());
                } else if ((event.type().equals("STAGE_SUCCEEDED")
                        || event.type().equals("FALLBACK_SUCCEEDED"))
                        && failures.containsKey(event.stageId())) {
                    totalMillis += Duration.between(failures.remove(event.stageId()), event.occurredAt()).toMillis();
                    recoveries++;
                }
            }
        }
        return recoveries == 0 ? 0 : (double) totalMillis / recoveries;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
