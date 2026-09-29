package dev.assessment.urlshortener.orchestration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeRequest(
        @NotBlank String sourceStageId,
        @NotBlank @Size(max = 2000) String changeSummary,
        @NotBlank @Size(max = 100) String actor) {
}

