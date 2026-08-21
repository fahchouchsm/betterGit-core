package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commitreport.CommitReportLimits;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportOutcome;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportRequest;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportStatus;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.diagram.CommitDiagramPlan;
import io.fahchouchsm.betterGitCore.testduration.TestDurationPlan;
import io.fahchouchsm.betterGitCore.testduration.TestDurationService;
import io.fahchouchsm.betterGitCore.testduration.TestSuiteFailedException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "commit",
        description = "Create a Git commit documented by a concise AI-generated Markdown report.",
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
            description = "Commit message used when AI reporting is disabled or skipped with --no-ai.")
    private String explicitMessage;

    @Option(names = "--no-ai", description = "Skip AI report generation for this commit.")
    private boolean skipAi;

    CommitCommand(CommitCommandDependencies dependencies, Path invocationDirectory) {
        this.dependencies = dependencies;
        this.invocationDirectory = invocationDirectory;
    }

    @Override
    public Integer call() throws IOException {
        Path projectPath = ProjectDirectoryResolver.resolve(invocationDirectory, requestedDirectory);
        if (!Files.isDirectory(projectPath)) {
            dependencies.console().failure("Repository directory does not exist or is not a directory: " + projectPath);
            return CommandLine.ExitCode.USAGE;
        }
        return commit(projectPath);
    }

    private int commit(Path projectPath) throws IOException {
        Optional<CommitReportOutcome> report = skipAi ? Optional.empty() : generateReport(projectPath);
        if (nothingToCommit(report)) {
            dependencies.console().info(
                    "Nothing to commit: no staged changes were found. "
                            + "Stage a non-ignored file with 'bettergit add <path>' and try again.");
            return CommandLine.ExitCode.OK;
        }
        if (requiredReportFailed(report)) {
            dependencies.console().failure("Commit cancelled because its AI report could not be generated.");
            return CommandLine.ExitCode.SOFTWARE;
        }
        String commitMessage = commitMessage(report);
        if (commitMessage == null) {
            dependencies.console().failure(
                    "A commit message is required. Supply -m MESSAGE or configure AI commit reports with 'bettergit init'.");
            return CommandLine.ExitCode.USAGE;
        }
        String commitHash = dependencies.commitExecutor().commit(projectPath, commitMessage);
        dependencies.console().success("Committed " + commitHash.substring(0, 8) + " · " + commitMessage);
        finalizeReport(projectPath, report, commitHash);
        generateDiagram(projectPath, commitHash);
        trackTestDuration(projectPath, commitHash);
        return CommandLine.ExitCode.OK;
    }

    private void trackTestDuration(Path projectPath, String commitHash) {
        try {
            trackTestDurationIfEnabled(projectPath, commitHash);
        } catch (TestSuiteFailedException exception) {
            dependencies.console().warning("Tests failed after "
                    + TestDurationService.readableDuration(exception.duration())
                    + "; duration saved: " + exception.reportPath());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            dependencies.console().warning("Commit succeeded, but test-duration tracking was interrupted.");
        } catch (IOException exception) {
            dependencies.console().warning(
                    "Commit succeeded, but test duration could not be tracked: " + exception.getMessage());
        }
    }

    private void trackTestDurationIfEnabled(Path projectPath, String commitHash)
            throws IOException, InterruptedException, TestSuiteFailedException {
        Optional<TestDurationPlan> plan = dependencies.testDurationService()
                .planForCommit(projectPath, commitHash);
        if (plan.isPresent()) {
            TestDurationPlan enabledPlan = plan.orElseThrow();
            dependencies.testDurationService().track(enabledPlan);
            dependencies.console().success("Test duration saved: " + enabledPlan.outputFile());
        }
    }

    private void generateDiagram(Path projectPath, String commitHash) {
        try {
            Optional<CommitDiagramPlan> diagram = dependencies.diagramService()
                    .planForCommit(projectPath, commitHash);
            if (diagram.isPresent()) {
                CommitDiagramPlan plan = diagram.orElseThrow();
                dependencies.diagramService().generate(plan);
                dependencies.console().success("Class diagram saved: " + plan.outputFile());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            dependencies.console().warning(
                    "Commit succeeded, but class-diagram generation was interrupted.");
        } catch (IOException exception) {
            dependencies.console().warning(
                    "Commit succeeded, but its class diagram could not be generated: " + exception.getMessage());
        }
    }

    private Optional<CommitReportOutcome> generateReport(Path projectPath) throws IOException {
        Optional<BetterGitConfiguration> storedConfiguration =
                dependencies.configurationLoader().load(projectPath);
        if (storedConfiguration.isEmpty()) {
            dependencies.console().warning("No BetterGit configuration found in " + projectPath
                    + ". Run 'bettergit init " + projectPath + "' or pass an initialized directory.");
            return Optional.empty();
        }
        return Optional.of(generateReport(projectPath, storedConfiguration.orElseThrow()));
    }

    private CommitReportOutcome generateReport(Path projectPath, BetterGitConfiguration configuration)
            throws IOException {
        if (configuration.ai().commitReportEnabled()) {
            dependencies.fileStore().ensureReportsIgnored(projectPath);
        }
        CommitConfiguration commitConfiguration = commitConfiguration(projectPath, configuration);
        CommitReportOutcome outcome = dependencies.reportGenerator().generate(new CommitReportRequest(
                projectPath, commitConfiguration.betterGit(), commitConfiguration.ai(),
                CommitReportLimits.fromEnvironment(dependencies.environment())));
        reportSkip(outcome);
        return outcome;
    }

    private void reportSkip(CommitReportOutcome outcome) {
        switch (outcome.status()) {
            case AI_NOT_CONFIGURED -> dependencies.console().warning(
                    "AI report skipped: configure AI_API_KEY, AI_API_MODEL, and AI_API_URL.");
            case AI_REQUEST_FAILED -> dependencies.console().warning(
                    "AI report skipped because the provider request failed.");
            case INVALID_AI_RESPONSE -> dependencies.console().warning(
                    "AI report skipped because the response was invalid.");
            default -> {
            }
        }
        if (outcome.diagnosticPath() != null) {
            dependencies.console().warning("AI failure details saved: " + outcome.diagnosticPath());
        }
    }

    private String commitMessage(Optional<CommitReportOutcome> report) {
        Optional<String> generatedMessage = report
                .filter(outcome -> outcome.status() == CommitReportStatus.GENERATED)
                .map(CommitReportOutcome::commitMessage);
        if (generatedMessage.isPresent()) {
            return generatedMessage.orElseThrow();
        }
        if (explicitMessage != null && !explicitMessage.isBlank()) {
            return explicitMessage.strip();
        }
        return null;
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
                    projectPath, report.commitMessage(), commitHash);
        } catch (IOException exception) {
            dependencies.console().warning(
                    "The report was saved, but BetterGit recent history could not be updated.");
        }
    }

    private CommitConfiguration commitConfiguration(
            Path projectPath, BetterGitConfiguration configuration) throws IOException {
        AiConfiguration loadedAi = dependencies.aiConfigurationLoader().load(
                projectPath, dependencies.environment(), configuration.ai().model());
        AiConfiguration completedAi = configuration.ai().commitReportEnabled() && !loadedAi.isComplete()
                ? dependencies.aiSetupService().complete(projectPath, loadedAi, dependencies.console())
                : loadedAi;
        if (!completedAi.isComplete() || Objects.equals(configuration.ai().model(), completedAi.model())) {
            return new CommitConfiguration(configuration, completedAi);
        }
        BetterGitConfiguration updated = configuration.withAiModel(completedAi.model());
        dependencies.fileStore().writeConfiguration(projectPath, updated);
        return new CommitConfiguration(updated, completedAi);
    }

    private static boolean requiredReportFailed(Optional<CommitReportOutcome> report) {
        return report.filter(outcome -> outcome.status() != CommitReportStatus.DISABLED)
                .filter(outcome -> outcome.status() != CommitReportStatus.GENERATED)
                .isPresent();
    }

    private static boolean nothingToCommit(Optional<CommitReportOutcome> report) {
        return report.filter(outcome -> outcome.status() == CommitReportStatus.NO_MEANINGFUL_CHANGES)
                .isPresent();
    }

    private record CommitConfiguration(BetterGitConfiguration betterGit, AiConfiguration ai) {
    }
}
