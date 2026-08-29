package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.sonarqube.SonarQubeEvent;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "merge",
        description = "Merge a branch with an optional SonarQube quality gate.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class MergeCommand implements Callable<Integer> {
    private final MergeCommandDependencies dependencies;
    private final Path invocationDirectory;

    @Parameters(index = "0", paramLabel = "BRANCH", description = "Branch to merge into the current branch.")
    private String sourceBranch;

    @picocli.CommandLine.Option(names = {"-m", "--message"}, paramLabel = "MESSAGE",
            description = "Merge commit message.")
    private String message;

    MergeCommand(MergeCommandDependencies dependencies, Path invocationDirectory) {
        this.dependencies = dependencies;
        this.invocationDirectory = invocationDirectory;
    }

    @Override
    public Integer call() throws IOException {
        Path projectPath = invocationDirectory;
        if (!Files.isDirectory(projectPath)) {
            dependencies.console().failure("Repository directory does not exist or is not a directory: " + projectPath);
            return CommandLine.ExitCode.USAGE;
        }
        try {
            return merge(projectPath);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            dependencies.console().failure("Merge was interrupted.");
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private int merge(Path projectPath) throws IOException, InterruptedException {
        String targetBranch = dependencies.branchReader().currentBranch(projectPath);
        if (!dependencies.sonarQubeGate().isEnabledFor(projectPath, SonarQubeEvent.MERGE, targetBranch)) {
            return dependencies.mergeExecutor().merge(projectPath, sourceBranch, message);
        }
        int mergeExitCode = dependencies.mergeExecutor().beginGatedMerge(projectPath, sourceBranch);
        if (mergeExitCode != 0) {
            dependencies.console().failure(
                    "Merge stopped before SonarQube analysis. Resolve Git conflicts or abort the merge.");
            return mergeExitCode;
        }
        if (!dependencies.mergeExecutor().hasPendingMerge(projectPath)) {
            dependencies.console().info("Already up to date; no merge commit was created.");
            return CommandLine.ExitCode.OK;
        }
        return finishGatedMerge(projectPath, targetBranch);
    }

    private int finishGatedMerge(Path projectPath, String targetBranch)
            throws IOException, InterruptedException {
        try {
            if (dependencies.sonarQubeGate().runAndApprove(projectPath, SonarQubeEvent.MERGE, targetBranch)) {
                return dependencies.mergeExecutor().completeGatedMerge(projectPath, message);
            }
        } catch (IOException | InterruptedException exception) {
            abortAfterAnalysisFailure(projectPath, exception);
            throw exception;
        }
        dependencies.mergeExecutor().abortGatedMerge(projectPath);
        dependencies.console().failure("Merge cancelled by the SonarQube quality gate; Git merge was aborted.");
        return CommandLine.ExitCode.SOFTWARE;
    }

    private void abortAfterAnalysisFailure(Path projectPath, Exception analysisFailure) {
        try {
            dependencies.mergeExecutor().abortGatedMerge(projectPath);
        } catch (IOException | InterruptedException abortFailure) {
            analysisFailure.addSuppressed(abortFailure);
            dependencies.console().failure(
                    "SonarQube analysis failed and Git could not abort the merge. Run 'git merge --abort'.");
        }
    }
}
