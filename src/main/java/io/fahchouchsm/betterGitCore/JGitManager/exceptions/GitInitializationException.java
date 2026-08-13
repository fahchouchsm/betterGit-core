package io.fahchouchsm.betterGitCore.JGitManager.exceptions;

/** Indicates that JGit could not initialize a repository. */
public final class GitInitializationException extends IllegalStateException {
    public GitInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
