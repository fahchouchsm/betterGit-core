package io.fahchouchsm.betterGitCore.commitreport;

import java.nio.file.Path;

public record CommitReportOutcome(
        CommitReportStatus status,
        Path reportPath,
        Path diagnosticPath,
        String commitMessage,
        boolean inputTruncated) {

    public static CommitReportOutcome skipped(CommitReportStatus status) {
        return new CommitReportOutcome(status, null, null, null, false);
    }

    public static CommitReportOutcome failed(CommitReportStatus status, Path diagnosticPath) {
        return new CommitReportOutcome(status, null, diagnosticPath, null, false);
    }

    public static CommitReportOutcome generated(
            Path reportPath, String commitMessage, PromptPayload prompt) {
        return new CommitReportOutcome(
                CommitReportStatus.GENERATED, reportPath, null, commitMessage, prompt.truncated());
    }
}
