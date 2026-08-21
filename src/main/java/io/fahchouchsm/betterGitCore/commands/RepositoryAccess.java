package io.fahchouchsm.betterGitCore.commands;

import java.nio.file.Path;

/** Git operations required by command orchestration. */
public interface RepositoryAccess {
    boolean hasRepository(Path projectPath);

    void initialize(Path projectPath);
}
