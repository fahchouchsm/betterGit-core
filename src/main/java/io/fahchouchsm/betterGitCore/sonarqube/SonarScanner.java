package io.fahchouchsm.betterGitCore.sonarqube;

import java.io.IOException;

public interface SonarScanner {
    int run(SonarAnalysisRequest request) throws IOException, InterruptedException;
}
