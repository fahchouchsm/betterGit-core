package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.JGitManager.JGitManager;

import java.nio.file.Path;

/** Delegates command-level Git operations to the existing JGit package. */
public final class JGitRepositoryAccess implements RepositoryAccess {
    private final JGitManager gitManager;

    public JGitRepositoryAccess(JGitManager gitManager) {
        this.gitManager = gitManager;
    }

    @Override
    public boolean isInsideRepository(Path projectPath) {
        return gitManager.isInsideRepository(projectPath);
    }

    @Override
    public void initialize(Path projectPath) {
        gitManager.initializeRepository(projectPath);
    }
}
