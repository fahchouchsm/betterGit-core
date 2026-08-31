package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.documentation.AiTextGenerator;

import java.time.Clock;

public record CommitReportDependencies(
        CommitDataSource commitDataSource,
        AiTextGenerator aiTextGenerator,
        AiMemoryStore memoryStore,
        AiCommitContextBuilder contextBuilder,
        AiCommitPromptBuilder promptBuilder,
        SensitiveContentFilter filter,
        CommitReportValidator validator,
        CommitReportStore reportStore,
        Clock clock) {
}
