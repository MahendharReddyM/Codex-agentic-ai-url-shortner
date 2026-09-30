package dev.assessment.urlshortener.orchestration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalRequest(
        @NotBlank String stageId,
        @NotNull Boolean approved,
        @Size(max = 1000) String comment) {
}

