package io.fahchouchsm.betterGitCore.commitreport;

import java.util.List;

public record ValidatedCommitReport(
        String markdown,
        String commitMessage,
        List<String> changedAreas) {

    public ValidatedCommitReport {
        changedAreas = List.copyOf(changedAreas);
    }
}
