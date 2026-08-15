package io.fahchouchsm.betterGitCore.history;

public record RepositorySummary(
        String name,
        String branch,
        boolean detached,
        String state,
        boolean clean,
        int stagedFiles,
        int unstagedFiles,
        int untrackedFiles,
        int conflictedFiles,
        String upstream,
        int ahead,
        int behind,
        boolean betterGitInitialized,
        int betterGitReports) {
}
