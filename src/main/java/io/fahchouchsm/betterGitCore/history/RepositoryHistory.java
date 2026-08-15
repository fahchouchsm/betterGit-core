package io.fahchouchsm.betterGitCore.history;

import java.nio.file.Path;
import java.util.List;

public record RepositoryHistory(Path repository, RepositorySummary summary, List<HistoryCommit> commits) {
}
