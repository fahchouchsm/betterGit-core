package io.fahchouchsm.betterGitCore.JGitManager.exceptions;

/** Indicates that a repository path is missing, invalid, or cannot be accessed. */
public final class GitRepositoryPathException extends IllegalArgumentException {
    public GitRepositoryPathException(String message) {
        super(message);
    }

    public GitRepositoryPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
