package io.fahchouchsm.betterGitCore.testduration;

import java.nio.file.Path;

public record TestDurationPlan(Path projectPath, String commitHash, Path outputFile) {
}
