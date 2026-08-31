package io.fahchouchsm.betterGitCore.sonarqube;

import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfiguration;

import java.nio.file.Path;

public record SonarAnalysisRequest(
        Path projectPath,
        SonarQubeConfiguration configuration,
        Path metadataFile) {
}
