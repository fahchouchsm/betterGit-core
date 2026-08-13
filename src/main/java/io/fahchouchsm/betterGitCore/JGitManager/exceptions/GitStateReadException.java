package io.fahchouchsm.betterGitCore.JGitManager.exceptions;

/** Indicates that Git status or diff information could not be read. */
public final class GitStateReadException extends IllegalStateException {
    public GitStateReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
