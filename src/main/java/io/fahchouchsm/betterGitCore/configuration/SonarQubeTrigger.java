package io.fahchouchsm.betterGitCore.configuration;

public enum SonarQubeTrigger {
    COMMITS,
    MERGES,
    COMMITS_AND_MERGES;

    public boolean includesCommit() {
        return this == COMMITS || this == COMMITS_AND_MERGES;
    }

    public boolean includesMerge() {
        return this == MERGES || this == COMMITS_AND_MERGES;
    }
}
