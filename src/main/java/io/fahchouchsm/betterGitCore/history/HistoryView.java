package io.fahchouchsm.betterGitCore.history;

public record HistoryView(
        boolean details,
        boolean files,
        boolean fullHash,
        boolean graph,
        boolean statistics) {
}
