package io.fahchouchsm.betterGitCore.sonarqube;

import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfiguration;

import java.io.IOException;

public interface SonarQualityGateClient {
    SonarQualityGate fetch(SonarQubeConfiguration configuration) throws IOException, InterruptedException;
}
