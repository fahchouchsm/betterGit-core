package io.fahchouchsm.betterGitCore.sonarqube;

import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfigurationLoader;

import java.util.Map;

public record SonarQubeServiceDependencies(
        BetterGitConfigurationLoader betterGitConfigurationLoader,
        SonarQubeConfigurationLoader sonarQubeConfigurationLoader,
        SonarScanner scanner,
        SonarQualityGateClient qualityGateClient,
        Map<String, String> environment) {
}
