package io.fahchouchsm.betterGitCore.sonarqube;

public record SonarCondition(
        String metricKey,
        String status,
        String actualValue,
        String errorThreshold) {
}
