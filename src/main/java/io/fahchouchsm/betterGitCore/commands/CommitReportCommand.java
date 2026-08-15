package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commitreport.CommitReportLimits;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportOutcome;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportRequest;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "commit-report",
        description = "Generate an AI report for the staged commit changes.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class CommitReportCommand implements Callable<Integer> {
    private final CommitReportCommandDependencies dependencies;
    private final Path invocationDirectory;

    @Parameters(
            index = "0",
            arity = "0..1",
            paramLabel = "DIRECTORY",
            description = "Project directory to inspect (default: current directory).")
    private Path requestedDirectory;

    CommitReportCommand(CommitReportCommandDependencies dependencies, Path invocationDirectory) {
        this.dependencies = dependencies;
        this.invocationDirectory = invocationDirectory;
    }

    @Override
    public Integer call() throws Exception {
        Path projectPath = ProjectDirectoryResolver.resolve(invocationDirectory, requestedDirectory);
        if (!Files.isDirectory(projectPath)) {
            dependencies.console().failure(
                    "Project directory does not exist or is not a directory: " + projectPath);
            return CommandLine.ExitCode.USAGE;
        }
        Optional<BetterGitConfiguration> storedConfiguration = dependencies.configurationLoader().load(projectPath);
        if (storedConfiguration.isEmpty()) {
            dependencies.console().warning("AI commit report skipped: run 'bettergit init' first.");
            return CommandLine.ExitCode.OK;
        }
        return generate(projectPath, storedConfiguration.orElseThrow());
    }

    private int generate(Path projectPath, BetterGitConfiguration configuration) throws Exception {
        if (configuration.ai().commitReportEnabled()) {
            dependencies.fileStore().ensureReportsIgnored(projectPath);
        }
        AiConfiguration aiConfiguration = dependencies.aiConfigurationLoader().load(
                projectPath, dependencies.environment(), configuration.ai().model());
        CommitReportOutcome outcome = dependencies.generator().generate(new CommitReportRequest(
                projectPath,
                configuration,
                aiConfiguration,
                CommitReportLimits.fromEnvironment(dependencies.environment())));
        reportOutcome(outcome);
        return CommandLine.ExitCode.OK;
    }

    private void reportOutcome(CommitReportOutcome outcome) {
        switch (outcome.status()) {
            case GENERATED -> reportSuccess(outcome);
            case DISABLED -> dependencies.console().info(
                    "AI commit reports are disabled. Run 'bettergit init' to enable them.");
            case AI_NOT_CONFIGURED -> dependencies.console().warning(
                    "AI commit report skipped: configure AI_API_KEY, AI_API_MODEL, and AI_API_URL.");
            case NO_MEANINGFUL_CHANGES -> dependencies.console().info(
                    "AI commit report skipped: no meaningful staged changes were found.");
            case AI_REQUEST_FAILED -> dependencies.console().warning(
                    "AI commit report skipped because the AI request failed; Git changes were not affected.");
            case INVALID_AI_RESPONSE -> dependencies.console().warning(
                    "AI commit report skipped because the AI response was not a valid commit report.");
        }
    }

    private void reportSuccess(CommitReportOutcome outcome) {
        dependencies.console().success("AI commit report saved: " + outcome.reportPath());
        dependencies.console().info("Suggested commit message: " + outcome.suggestedCommitMessage());
        if (outcome.inputTruncated()) {
            dependencies.console().warning("AI input was truncated to the configured safe size limit.");
        }
    }
}
