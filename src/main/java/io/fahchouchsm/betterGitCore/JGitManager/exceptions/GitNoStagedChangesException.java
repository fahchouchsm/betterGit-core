package io.fahchouchsm.betterGitCore.JGitManager.exceptions;

/** Indicates that documentation was requested before any changes were staged for the next commit. */
public final class GitNoStagedChangesException extends IllegalStateException {
    public GitNoStagedChangesException(String message) {
        super(message);
    }
}
