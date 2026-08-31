package io.fahchouchsm.betterGitCore.api;

import io.fahchouchsm.betterGitCore.commitreport.CommitReportOutcome;

import java.nio.file.Path;

public record CommitDocumentation(
        String status,
        String markdown,
        String commitMessage,
        String reportPath,
        String diagnosticPath,
        boolean inputTruncated) {

    static CommitDocumentation from(CommitReportOutcome outcome, String markdown) {
        return new CommitDocumentation(
                outcome.status().name(), markdown, text(outcome.commitMessage()),
                path(outcome.reportPath()), path(outcome.diagnosticPath()), outcome.inputTruncated());
    }

    private static String path(Path path) {
        return path == null ? "" : path.toString();
    }

    private static String text(String text) {
        return text == null ? "" : text;
    }
}
