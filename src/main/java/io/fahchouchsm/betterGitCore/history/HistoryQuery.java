package io.fahchouchsm.betterGitCore.history;

import java.time.Instant;

public record HistoryQuery(
        int limit,
        boolean allReferences,
        String author,
        String message,
        Instant since,
        Instant until,
        String path,
        boolean reverse,
        boolean includeChanges) {

    public HistoryQuery {
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("History limit must be between 1 and 1000.");
        }
    }
}
