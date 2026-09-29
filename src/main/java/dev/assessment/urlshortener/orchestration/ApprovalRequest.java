package dev.assessment.urlshortener.orchestration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApprovalRequest(
        @NotBlank String stageId,
        @NotBlank @Size(max = 100) String approver,
        @Size(max = 1000) String comment) {
}

