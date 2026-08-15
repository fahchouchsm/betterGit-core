package io.fahchouchsm.betterGitCore.history;

import java.util.List;

public record HistoryCommit(
        String hash,
        String subject,
        String message,
        CommitIdentity author,
        CommitIdentity committer,
        List<String> parents,
        String treeHash,
        String encoding,
        List<CommitFooter> footers,
        List<String> decorations,
        boolean signed,
        String betterGitReport,
        CommitStatistics statistics,
        List<CommitFileChange> files) {

    public boolean merge() {
        return parents.size() > 1;
    }
}
