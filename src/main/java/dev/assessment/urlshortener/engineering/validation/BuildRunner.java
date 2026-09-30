package dev.assessment.urlshortener.engineering.validation;

import java.nio.file.Path;

import dev.assessment.urlshortener.engineering.EngineeringModels.BuildEvidence;

public interface BuildRunner {
    BuildEvidence verify(Path workspace);
}
