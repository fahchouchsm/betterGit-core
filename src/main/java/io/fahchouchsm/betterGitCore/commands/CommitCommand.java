package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commitreport.CommitReportLimits;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportOutcome;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportRequest;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportStatus;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "commit",
        description = "Create a Git commit with an optional AI-generated report and message.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class CommitCommand implements Callable<Integer> {
    private final CommitCommandDependencies dependencies;
    private final Path invocationDirectory;

    @Parameters(index = "0", arity = "0..1", paramLabel = "DIRECTORY",
            description = "Repository directory to commit (default: current directory).")
    private Path requestedDirectory;

    @Option(names = {"-m", "--message"}, paramLabel = "MESSAGE",
            description = "Commit message. Overrides an AI-suggested message.")
    private String explicitMessage;

    @Option(names = "--no-ai", description = "Skip AI report generation for this commit.")
    private boolean skipAi;

    CommitCommand(CommitCommandDependencies dependencies, Path invocationDirectory) {
        this.dependencies = dependencies;
        this.invocationDirectory = invocationDirectory;
    }

    @Override
    public Integer call() {
        Path projectPath = ProjectDirectoryResolver.resolve(invocationDirectory, requestedDirectory);
        if (!Files.isDirectory(projectPath)) {
            dependencies.console().failure("Repository directory does not exist or is not a directory: " + projectPath);
            return CommandLine.ExitCode.USAGE;
        }
        return commit(projectPath);
    }

    private int commit(Path projectPath) {
        Optional<CommitReportOutcome> report = skipAi ? Optional.empty() : generateReport(projectPath);
        String commitMessage = commitMessage(report);
        if (commitMessage == null) {
            dependencies.console().failure(
                    "A commit message is required. Supply -m MESSAGE or configure AI commit reports with 'bettergit init'.");
            return CommandLine.ExitCode.USAGE;
        }
        String commitHash = dependencies.commitExecutor().commit(projectPath, commitMessage);
        dependencies.console().success("Committed " + commitHash.substring(0, 8) + " · " + commitMessage);
        finalizeReport(projectPath, report, commitHash);
        return CommandLine.ExitCode.OK;
    }

    private Optional<CommitReportOutcome> generateReport(Path projectPath) {
        Optional<BetterGitConfiguration> storedConfiguration;
        try {
            storedConfiguration = dependencies.configurationLoader().load(projectPath);
            if (storedConfiguration.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(generateReport(projectPath, storedConfiguration.orElseThrow()));
        } catch (IOException exception) {
            dependencies.console().warning(
                    "AI commit report skipped because local BetterGit context could not be read.");
            return Optional.empty();
        }
    }

    private CommitReportOutcome generateReport(Path projectPath, BetterGitConfiguration configuration)
            throws IOException {
        if (configuration.ai().commitReportEnabled()) {
            dependencies.fileStore().ensureReportsIgnored(projectPath);
        }
        AiConfiguration aiConfiguration = dependencies.aiConfigurationLoader().load(
                projectPath, dependencies.environment(), configuration.ai().model());
        CommitReportOutcome outcome = dependencies.reportGenerator().generate(new CommitReportRequest(
                projectPath, configuration, aiConfiguration,
                CommitReportLimits.fromEnvironment(dependencies.environment())));
        reportSkip(outcome.status());
        return outcome;
    }

    private void reportSkip(CommitReportStatus status) {
        switch (status) {
            case AI_NOT_CONFIGURED -> dependencies.console().warning(
                    "AI report skipped: configure AI_API_KEY, AI_API_MODEL, and AI_API_URL.");
            case AI_REQUEST_FAILED -> dependencies.console().warning(
                    "AI report skipped because the provider request failed.");
            case INVALID_AI_RESPONSE -> dependencies.console().warning(
                    "AI report skipped because the response was invalid.");
            default -> {
            }
        }
    }

    private String commitMessage(Optional<CommitReportOutcome> report) {
        if (explicitMessage != null && !explicitMessage.isBlank()) {
            return explicitMessage.strip();
        }
        return report.filter(outcome -> outcome.status() == CommitReportStatus.GENERATED)
                .map(CommitReportOutcome::suggestedCommitMessage)
                .orElse(null);
    }

    private void finalizeReport(
            Path projectPath, Optional<CommitReportOutcome> report, String commitHash) {
        report.filter(outcome -> outcome.status() == CommitReportStatus.GENERATED)
                .ifPresent(outcome -> finalizeReport(projectPath, outcome, commitHash));
    }

    private void finalizeReport(Path projectPath, CommitReportOutcome report, String commitHash) {
        try {
            Path finalPath = dependencies.reportStore().finalizePending(
                    projectPath, report.reportPath(), commitHash);
            dependencies.console().success("AI commit report saved: " + finalPath);
            finalizeHistory(projectPath, report, commitHash);
            if (report.inputTruncated()) {
                dependencies.console().warning("AI input was truncated to the configured safe size limit.");
            }
        } catch (IOException exception) {
            dependencies.console().warning(
                    "Commit succeeded, but its AI report could not be finalized; the pending report was preserved.");
        }
    }

    private void finalizeHistory(Path projectPath, CommitReportOutcome report, String commitHash) {
        try {
            dependencies.memoryStore().finalizePendingHistory(
                    projectPath, report.suggestedCommitMessage(), commitHash);
        } catch (IOException exception) {
            dependencies.console().warning(
                    "The report was saved, but BetterGit recent history could not be updated.");
        }
    }
}
