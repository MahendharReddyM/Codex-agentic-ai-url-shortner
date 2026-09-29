package dev.assessment.urlshortener.orchestration;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    public OrchestrationController(OrchestrationService service) {
        this.service = service;
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
    WorkflowRunView approve(@PathVariable String id, @Valid @RequestBody ApprovalRequest request) {
        return service.approve(id, request);
    }

    @PostMapping("/{id}/resume")
    WorkflowRunView resume(@PathVariable String id) {
        return service.resume(id);
    }
}

