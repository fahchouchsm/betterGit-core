package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;

import java.nio.file.Path;

public record CommitReportRequest(
        Path projectPath,
        BetterGitConfiguration betterGitConfiguration,
        AiConfiguration aiConfiguration,
        CommitReportLimits limits) {
}
