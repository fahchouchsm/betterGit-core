package io.fahchouchsm.betterGitCore.JGitManager.exceptions;

/** Indicates that the supplied directory is not an existing Git repository. */
public final class GitRepositoryNotFoundException extends IllegalArgumentException {
    public GitRepositoryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
