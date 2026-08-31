package io.fahchouchsm.betterGitCore.diagram;

import java.nio.file.Path;

public record CommitDiagramPlan(Path projectPath, Path javaSources, Path outputFile) {
}
