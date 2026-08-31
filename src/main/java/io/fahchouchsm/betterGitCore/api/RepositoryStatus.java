package io.fahchouchsm.betterGitCore.api;

import java.nio.file.Path;
import java.util.List;

public record RepositoryStatus(
        Path repository,
        String name,
        String branch,
        boolean betterGitInitialized,
        List<ChangedPath> staged,
        List<ChangedPath> unstaged) {

    public RepositoryStatus {
        staged = List.copyOf(staged);
        unstaged = List.copyOf(unstaged);
    }

    public boolean clean() {
        return staged.isEmpty() && unstaged.isEmpty();
    }
}
