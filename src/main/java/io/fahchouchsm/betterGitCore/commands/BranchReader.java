package io.fahchouchsm.betterGitCore.commands;

import java.nio.file.Path;

@FunctionalInterface
public interface BranchReader {
    String currentBranch(Path projectPath);
}
