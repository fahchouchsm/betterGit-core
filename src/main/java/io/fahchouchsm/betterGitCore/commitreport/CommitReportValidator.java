package io.fahchouchsm.betterGitCore.commitreport;

import java.util.Arrays;
import java.util.List;

public final class CommitReportValidator {
    private static final List<String> HEADINGS = List.of(
            "# Commit Report",
            "## Suggested commit message",
            "## Summary",
            "## Changed areas",
            "## Technical details",
            "## Validation",
            "## Risks or follow-up");

    public ValidatedCommitReport validate(String markdown) {
        if (markdown == null || markdown.isBlank() || !hasOrderedHeadings(markdown)) {
            throw new IllegalArgumentException("AI commit report is missing required Markdown headings.");
        }
        String commitMessage = section(markdown, HEADINGS.get(1), HEADINGS.get(2));
        String summary = section(markdown, HEADINGS.get(2), HEADINGS.get(3));
        List<String> changedAreas = bulletLines(section(markdown, HEADINGS.get(3), HEADINGS.get(4)));
        if (commitMessage.isBlank() || summary.isBlank()) {
            throw new IllegalArgumentException("AI commit report contains an empty required section.");
        }
        return new ValidatedCommitReport(
                markdown.strip() + System.lineSeparator(), commitMessage.lines().findFirst().orElseThrow(),
                summary, changedAreas);
    }

    private static boolean hasOrderedHeadings(String markdown) {
        int previousIndex = -1;
        for (String heading : HEADINGS) {
            int headingIndex = markdown.indexOf(heading, previousIndex + 1);
            if (headingIndex < 0) {
                return false;
            }
            previousIndex = headingIndex;
        }
        return true;
    }

    private static String section(String markdown, String heading, String nextHeading) {
        int start = markdown.indexOf(heading) + heading.length();
        int end = markdown.indexOf(nextHeading, start);
        return markdown.substring(start, end).strip();
    }

    private static List<String> bulletLines(String section) {
        return Arrays.stream(section.split("\\R"))
                .map(String::strip)
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2).strip())
                .filter(line -> !line.isBlank())
                .toList();
    }
}
