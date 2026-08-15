package io.fahchouchsm.betterGitCore.commitreport;

import java.nio.file.Path;

public record CommitReportOutcome(
        CommitReportStatus status,
        Path reportPath,
        String suggestedCommitMessage,
        boolean inputTruncated) {

    public static CommitReportOutcome skipped(CommitReportStatus status) {
        return new CommitReportOutcome(status, null, null, false);
    }

    public static CommitReportOutcome generated(
            Path reportPath, String suggestedCommitMessage, PromptPayload prompt) {
        return new CommitReportOutcome(
                CommitReportStatus.GENERATED, reportPath, suggestedCommitMessage, prompt.truncated());
    }
}
