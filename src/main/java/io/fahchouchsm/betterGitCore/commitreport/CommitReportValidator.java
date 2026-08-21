package io.fahchouchsm.betterGitCore.commitreport;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommitReportValidator {
    private static final int MAXIMUM_DESCRIPTION_CHARACTERS = 160;
    private static final int MAXIMUM_VALIDATION_CHARACTERS = 240;
    private static final int MAXIMUM_CHANGED_AREAS = 5;
    private static final String CHANGES_HEADING = "## Changes";
    private static final String VALIDATION_HEADING = "## Validation";
    private static final String CHANGES_MARKER = "\n" + CHANGES_HEADING + "\n";
    private static final String VALIDATION_MARKER = "\n" + VALIDATION_HEADING + "\n";
    private static final Pattern CHANGE_BULLET = Pattern.compile("[-*+]\\s+(\\S.*)");
    private static final List<String> HEADINGS = List.of(
            CHANGES_HEADING,
            VALIDATION_HEADING);

    public ValidatedCommitReport validate(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("AI commit report is missing required Markdown headings.");
        }
        String normalizedInput = markdown.replace("\r\n", "\n").replace('\r', '\n').strip();
        ReportSections sections = sections(normalizedInput);
        String description = normalizedDescription(sections.description());
        List<String> changedAreas = changedAreas(sections.changes());
        String validation = normalizedValidation(sections.validation());
        String normalizedMarkdown = normalizedMarkdown(description, changedAreas, validation);
        return new ValidatedCommitReport(
                normalizedMarkdown, description, changedAreas);
    }

    private static ReportSections sections(String markdown) {
        if (!markdown.lines()
                .filter(line -> line.startsWith("#"))
                .toList()
                .equals(HEADINGS)) {
            throw new IllegalArgumentException("AI commit report is missing required Markdown headings.");
        }
        int changesMarkerStart = markdown.indexOf(CHANGES_MARKER);
        int validationMarkerStart = markdown.indexOf(
                VALIDATION_MARKER, changesMarkerStart + CHANGES_MARKER.length());
        if (changesMarkerStart < 0 || validationMarkerStart < 0) {
            throw new IllegalArgumentException("AI commit report is missing required Markdown headings.");
        }
        int changesStart = changesMarkerStart + CHANGES_MARKER.length();
        int validationStart = validationMarkerStart + VALIDATION_MARKER.length();
        return new ReportSections(
                markdown.substring(0, changesMarkerStart),
                markdown.substring(changesStart, validationMarkerStart),
                markdown.substring(validationStart));
    }

    private static String normalizedDescription(String description) {
        String normalized = normalizedParagraph(
                description, "AI commit report must start with one description paragraph.");
        if (normalized.isBlank() || normalized.length() > MAXIMUM_DESCRIPTION_CHARACTERS) {
            throw new IllegalArgumentException("AI commit description must contain 1 to 160 characters.");
        }
        return normalized;
    }

    private static List<String> changedAreas(String changes) {
        List<String> lines = Arrays.stream(changes.split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty() || lines.size() > MAXIMUM_CHANGED_AREAS) {
            throw new IllegalArgumentException("AI commit report must contain one to five change bullets.");
        }
        return lines.stream().map(CommitReportValidator::normalizedChangeArea).toList();
    }

    private static String normalizedChangeArea(String line) {
        Matcher bullet = CHANGE_BULLET.matcher(line);
        if (!bullet.matches()) {
            throw new IllegalArgumentException("AI commit report must contain one to five change bullets.");
        }
        return bullet.group(1).strip();
    }

    private static String normalizedValidation(String validation) {
        String normalized = normalizedParagraph(
                validation, "AI commit validation must be one brief paragraph.");
        if (normalized.isBlank() || normalized.length() > MAXIMUM_VALIDATION_CHARACTERS) {
            throw new IllegalArgumentException("AI commit validation must contain 1 to 240 characters.");
        }
        return normalized;
    }

    private static String normalizedParagraph(String paragraph, String errorMessage) {
        if (paragraph.strip().split("\\R\\s*\\R", -1).length != 1) {
            throw new IllegalArgumentException(errorMessage);
        }
        return paragraph.replaceAll("\\s+", " ").strip();
    }

    private static String normalizedMarkdown(
            String description, List<String> changedAreas, String validation) {
        String separator = System.lineSeparator();
        String changes = changedAreas.stream()
                .map(area -> "- " + area)
                .reduce((left, right) -> left + separator + right)
                .orElseThrow();
        return description + separator + separator
                + CHANGES_HEADING + separator + changes + separator + separator
                + VALIDATION_HEADING + separator + validation + separator;
    }

    private record ReportSections(String description, String changes, String validation) {
    }
}
