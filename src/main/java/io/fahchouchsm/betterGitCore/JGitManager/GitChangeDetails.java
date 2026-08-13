package io.fahchouchsm.betterGitCore.JGitManager;

/** The textual Git patches that describe changes before a commit. */
public record GitChangeDetails(String stagedDiff, String unstagedDiff) {
    public GitChangeDetails {
        if (stagedDiff == null || unstagedDiff == null) {
            throw new IllegalArgumentException("Git diffs must not be null");
        }
    }

    public boolean hasStagedChanges() {
        return !stagedDiff.isBlank();
    }

    public boolean hasUnstagedChanges() {
        return !unstagedDiff.isBlank();
    }
}
