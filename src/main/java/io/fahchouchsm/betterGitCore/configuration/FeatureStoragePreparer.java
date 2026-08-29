package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Path;

public final class FeatureStoragePreparer {
    private final BetterGitFileStore fileStore;

    public FeatureStoragePreparer(BetterGitFileStore fileStore) {
        this.fileStore = fileStore;
    }

    public void prepare(Path projectPath, FeatureSettings settings) throws IOException {
        if (settings.classDiagramOnCommit()) {
            BetterGitDirectories.child(projectPath, "diagrams");
            fileStore.ensureDiagramsIgnored(projectPath);
        }
        if (settings.testDurationTracking()) {
            BetterGitDirectories.child(projectPath, "test-durations");
            fileStore.ensureTestDurationsIgnored(projectPath);
        }
        if (settings.sonarQubeDocumentation()) {
            BetterGitDirectories.child(projectPath, "sonarqube");
            fileStore.ensureSonarQubeIgnored(projectPath);
        }
    }
}
