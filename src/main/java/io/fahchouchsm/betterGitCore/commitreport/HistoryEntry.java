package io.fahchouchsm.betterGitCore.commitreport;

import java.time.Instant;
import java.util.List;

public record HistoryEntry(
        String commitHash,
        Instant date,
        String title,
        List<String> changedAreas,
        String summary) {

    public HistoryEntry {
        changedAreas = List.copyOf(changedAreas);
    }

    public String markdown() {
        String areas = changedAreas.isEmpty() ? "- Not available" : changedAreas.stream()
                .map(area -> "- " + area)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- Not available");
        return """
                ## %s · %s
                **Title:** %s

                **Changed areas:**
                %s

                **Summary:** %s
                """.formatted(commitHash, date, title, areas, summary).strip();
    }
}
