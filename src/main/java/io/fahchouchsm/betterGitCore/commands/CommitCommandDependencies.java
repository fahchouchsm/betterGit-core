package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commitreport.AiCommitReportGenerator;
import io.fahchouchsm.betterGitCore.commitreport.AiMemoryStore;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportStore;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;

import java.util.Map;

record CommitCommandDependencies(
        CommitExecutor commitExecutor,
        AiCommitReportGenerator reportGenerator,
        CommitReportStore reportStore,
        AiMemoryStore memoryStore,
        BetterGitConfigurationLoader configurationLoader,
        AiConfigurationLoader aiConfigurationLoader,
        BetterGitFileStore fileStore,
        ConsolePort console,
        Map<String, String> environment) {
}
