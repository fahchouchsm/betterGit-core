package io.fahchouchsm.betterGitCore.commitreport;

import java.util.List;

public record AiCommitContext(
        String branch,
        List<ChangedFile> changedFiles,
        DiffStatistics statistics,
        String stagedDiff,
        String validation,
        String generalProjectContext,
        String projectMap,
        String recentHistory,
        String selectiveSourceContext) {

    public AiCommitContext {
        changedFiles = List.copyOf(changedFiles);
    }
}
