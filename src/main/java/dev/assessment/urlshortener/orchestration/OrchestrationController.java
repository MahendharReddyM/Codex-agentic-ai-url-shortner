package dev.assessment.urlshortener.orchestration;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orchestration/runs")
public class OrchestrationController {
    private final OrchestrationService service;
    private final OrchestrationMetricsService metricsService;

    public OrchestrationController(OrchestrationService service, OrchestrationMetricsService metricsService) {
        this.service = service;
        this.metricsService = metricsService;
    }

    @PostMapping
    ResponseEntity<WorkflowRunView> start(@Valid @RequestBody StartRunRequest request) {
        WorkflowRunView run = service.start(request);
        return ResponseEntity.accepted().body(run);
    }

    @GetMapping("/{id}")
    WorkflowRunView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approvals")
    WorkflowRunView approve(
            @PathVariable String id,
            @Valid @RequestBody ApprovalRequest request,
            Authentication authentication) {
        return service.approve(id, request, authentication.getName());
    }

    @PostMapping("/{id}/resume")
    WorkflowRunView resume(@PathVariable String id) {
        return service.resume(id);
    }

    @PostMapping("/{id}/changes")
    WorkflowRunView replan(
            @PathVariable String id,
            @Valid @RequestBody ChangeRequest request,
            Authentication authentication) {
        return service.replan(id, request, authentication.getName());
    }

    @PostMapping("/{id}/cancel")
    WorkflowRunView cancel(
            @PathVariable String id,
            @Valid @RequestBody CancelRequest request,
            Authentication authentication) {
        return service.cancel(id, request, authentication.getName());
    }

    @GetMapping("/{id}/audit")
    AuditTrailView audit(@PathVariable String id) {
        return service.audit(id);
    }

    @GetMapping("/metrics")
    OrchestrationMetrics metrics() {
        return metricsService.snapshot();
    }
}
