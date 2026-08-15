package io.fahchouchsm.betterGitCore.commitreport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class AiCommitContextBuilder {
    private final SensitiveContentFilter filter;
    private final JavaSourceContextCollector sourceCollector;

    public AiCommitContextBuilder(SensitiveContentFilter filter, JavaSourceContextCollector sourceCollector) {
        this.filter = filter;
        this.sourceCollector = sourceCollector;
    }

    public AiCommitContext build(
            Path projectPath,
            CommitSnapshot snapshot,
            MemoryContext memory,
            String configuredSecret) throws IOException {
        List<ChangedFile> safeFiles = filter.safeFiles(snapshot.changedFiles());
        String safeDiff = filter.safeDiff(snapshot.stagedDiff(), configuredSecret);
        String safeSourceContext = filter.redact(
                sourceCollector.collect(projectPath, safeFiles, safeDiff), configuredSecret);
        return new AiCommitContext(
                filter.redact(snapshot.branch(), configuredSecret),
                promptFiles(safeFiles, configuredSecret),
                DiffStatistics.fromDiff(safeFiles.size(), safeDiff),
                safeDiff,
                filter.redact(snapshot.validation(), configuredSecret),
                filter.redact(memory.general(), configuredSecret),
                filter.redact(memory.projectMap(), configuredSecret),
                filter.redact(memory.recentHistory(), configuredSecret),
                safeSourceContext);
    }

    private List<ChangedFile> promptFiles(List<ChangedFile> safeFiles, String configuredSecret) {
        return safeFiles.stream()
                .map(file -> new ChangedFile(
                        file.status(),
                        filter.redact(file.path(), configuredSecret),
                        file.previousPath() == null
                                ? null
                                : filter.redact(file.previousPath(), configuredSecret)))
                .toList();
    }
}
