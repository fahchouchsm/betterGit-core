package io.fahchouchsm.betterGitCore.commitreport;

import java.util.List;

public record CommitSnapshot(
        String branch,
        List<ChangedFile> changedFiles,
        String stagedDiff,
        DiffStatistics statistics,
        String validation) {

    public CommitSnapshot {
        changedFiles = List.copyOf(changedFiles);
    }

    public boolean hasChanges() {
        return !changedFiles.isEmpty() && !stagedDiff.isBlank();
    }
}
