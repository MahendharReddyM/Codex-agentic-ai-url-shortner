package dev.assessment.urlshortener.engineering;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dev.assessment.urlshortener.engineering.EngineeringModels.CancelEngineeringRunRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringReliabilityMetrics;
import dev.assessment.urlshortener.engineering.EngineeringModels.EngineeringRunView;
import dev.assessment.urlshortener.engineering.EngineeringModels.HashApprovalRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.RequirementChangeRequest;
import dev.assessment.urlshortener.engineering.EngineeringModels.StartEngineeringRunRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engineering")
public class EngineeringController {
    private final EngineeringExecutionService service;

    public EngineeringController(EngineeringExecutionService service) {
        this.service = service;
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAnyRole('SUBMITTER', 'ADMIN')")
    @Operation(summary = "Start a governed engineering execution chain")
    ResponseEntity<EngineeringRunView> start(
            @Valid @RequestBody StartEngineeringRunRequest request,
            Authentication authentication) {
        EngineeringRunView view = service.start(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/v1/engineering/runs/" + view.id())).body(view);
    }

    @GetMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    List<EngineeringRunView> list() {
        return service.list();
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("isAuthenticated()")
    EngineeringRunView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/runs/{id}/approvals/change")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Approve the exact current plan hash and execute the governed change")
    EngineeringRunView approveChange(
            @PathVariable UUID id,
            @Valid @RequestBody HashApprovalRequest request,
            Authentication authentication) {
        return service.approveChange(id, request, authentication.getName(), roles(authentication));
    }

    @PostMapping("/runs/{id}/approvals/release")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Approve the exact validated outcome hash; this never deploys production")
    EngineeringRunView approveRelease(
            @PathVariable UUID id,
            @Valid @RequestBody HashApprovalRequest request,
            Authentication authentication) {
        return service.approveRelease(id, request, authentication.getName(), roles(authentication));
    }

    @PostMapping("/runs/{id}/changes")
    @PreAuthorize("hasAnyRole('SUBMITTER', 'ADMIN')")
    @Operation(summary = "Change an upstream requirement and dynamically re-plan in a new revision")
    EngineeringRunView replan(
            @PathVariable UUID id,
            @Valid @RequestBody RequirementChangeRequest request,
            Authentication authentication) {
        return service.replan(id, request, authentication.getName());
    }

    @PostMapping("/runs/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUBMITTER', 'APPROVER', 'ADMIN')")
    EngineeringRunView cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelEngineeringRunRequest request,
            Authentication authentication) {
        return service.cancel(id, request.reason(), authentication.getName());
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    EngineeringReliabilityMetrics metrics() {
        return service.metrics();
    }

    private List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream().map(Object::toString).toList();
    }
}
