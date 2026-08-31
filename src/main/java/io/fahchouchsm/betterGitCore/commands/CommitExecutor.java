package io.fahchouchsm.betterGitCore.commands;

import java.nio.file.Path;

@FunctionalInterface
public interface CommitExecutor {
    String commit(Path projectPath, String message);
}
