package dev.assessment.urlshortener.orchestration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StartRunRequest(
        @NotBlank @Size(min = 10, max = 4000) String requirement,
        @NotNull ScenarioType scenario) {
}

