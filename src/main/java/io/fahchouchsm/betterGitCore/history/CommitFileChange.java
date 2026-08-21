package io.fahchouchsm.betterGitCore.history;

public record CommitFileChange(
        String status,
        String path,
        String previousPath,
        int additions,
        int deletions,
        boolean binary) {
}
