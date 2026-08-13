package io.fahchouchsm.betterGitCore.commands;

import java.nio.file.Path;

/** Git operations required by command orchestration. */
public interface RepositoryAccess {
    boolean isInsideRepository(Path projectPath);

    void initialize(Path projectPath);
}
