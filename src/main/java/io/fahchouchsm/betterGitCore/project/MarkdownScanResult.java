package io.fahchouchsm.betterGitCore.project;

/** Bounded Markdown input and information about content omitted by scanner limits. */
public record MarkdownScanResult(
        String content,
        int includedFiles,
        int skippedFiles,
        int truncatedFiles) {

    public boolean contentWasLimited() {
        return skippedFiles > 0 || truncatedFiles > 0;
    }
}
