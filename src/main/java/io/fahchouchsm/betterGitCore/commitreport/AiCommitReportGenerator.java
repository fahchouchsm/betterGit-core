package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class AiCommitReportGenerator {
    private final CommitReportDependencies dependencies;

    public AiCommitReportGenerator(CommitReportDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public CommitReportOutcome generate(CommitReportRequest request) throws IOException {
        AiCommitSettings settings = request.betterGitConfiguration().ai();
        if (!settings.commitReportEnabled()) {
            return CommitReportOutcome.skipped(CommitReportStatus.DISABLED);
        }
        if (!request.aiConfiguration().isComplete()) {
            return CommitReportOutcome.skipped(CommitReportStatus.AI_NOT_CONFIGURED);
        }
        CommitSnapshot snapshot = dependencies.commitDataSource().stagedSnapshot(request.projectPath());
        if (!snapshot.hasChanges()) {
            return CommitReportOutcome.skipped(CommitReportStatus.NO_MEANINGFUL_CHANGES);
        }
        return generateFromSnapshot(request, settings, snapshot);
    }

    private CommitReportOutcome generateFromSnapshot(
            CommitReportRequest request, AiCommitSettings settings, CommitSnapshot snapshot) throws IOException {
        MemoryContext memory = memoryContext(request.projectPath(), settings);
        AiCommitContext context = dependencies.contextBuilder().build(
                request.projectPath(), snapshot, memory, request.aiConfiguration().apiKey());
        if (context.changedFiles().isEmpty() || context.stagedDiff().isBlank()) {
            return CommitReportOutcome.skipped(CommitReportStatus.NO_MEANINGFUL_CHANGES);
        }
        PromptPayload prompt = dependencies.promptBuilder().build(context, request.limits());
        return requestAiReport(new GenerationAttempt(request, settings, context, prompt));
    }

    private MemoryContext memoryContext(Path projectPath, AiCommitSettings settings) throws IOException {
        if (!settings.memoryEnabled()) {
            return MemoryContext.empty();
        }
        dependencies.memoryStore().initialize(projectPath);
        return dependencies.memoryStore().read(projectPath, AiMemoryStore.MAX_HISTORY_ENTRIES);
    }

    private CommitReportOutcome requestAiReport(GenerationAttempt attempt) throws IOException {
        String generatedMarkdown;
        try {
            generatedMarkdown = dependencies.aiTextGenerator()
                    .generate(attempt.request().aiConfiguration(), attempt.prompt().text());
        } catch (IOException exception) {
            return CommitReportOutcome.skipped(CommitReportStatus.AI_REQUEST_FAILED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CommitReportOutcome.skipped(CommitReportStatus.AI_REQUEST_FAILED);
        }
        return persistValidReport(attempt, generatedMarkdown);
    }

    private CommitReportOutcome persistValidReport(GenerationAttempt attempt, String generatedMarkdown)
            throws IOException {
        String safeMarkdown = dependencies.filter().redact(
                generatedMarkdown, attempt.request().aiConfiguration().apiKey());
        ValidatedCommitReport report;
        try {
            report = dependencies.validator().validate(safeMarkdown);
        } catch (IllegalArgumentException exception) {
            return CommitReportOutcome.skipped(CommitReportStatus.INVALID_AI_RESPONSE);
        }
        Instant createdAt = dependencies.clock().instant();
        Path reportPath = dependencies.reportStore()
                .savePending(attempt.request().projectPath(), createdAt, report.markdown());
        updateHistory(attempt, createdAt, report);
        return CommitReportOutcome.generated(
                reportPath, report.suggestedCommitMessage(), attempt.prompt());
    }

    private void updateHistory(GenerationAttempt attempt, Instant createdAt, ValidatedCommitReport report)
            throws IOException {
        if (!attempt.settings().memoryEnabled()) {
            return;
        }
        List<String> changedAreas = report.changedAreas().isEmpty()
                ? attempt.context().changedFiles().stream().map(ChangedFile::path).toList()
                : report.changedAreas();
        dependencies.memoryStore().appendHistory(attempt.request().projectPath(), new HistoryEntry(
                "pending", createdAt, report.suggestedCommitMessage(), changedAreas, report.summary()));
    }

    private record GenerationAttempt(
            CommitReportRequest request,
            AiCommitSettings settings,
            AiCommitContext context,
            PromptPayload prompt) {
    }
}
