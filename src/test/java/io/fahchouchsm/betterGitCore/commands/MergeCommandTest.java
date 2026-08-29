package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeFailurePolicy;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeSettings;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeTrigger;
import io.fahchouchsm.betterGitCore.sonarqube.SonarCondition;
import io.fahchouchsm.betterGitCore.sonarqube.SonarQualityGate;
import io.fahchouchsm.betterGitCore.sonarqube.SonarQubeService;
import io.fahchouchsm.betterGitCore.sonarqube.SonarQubeServiceDependencies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeCommandTest {
    @TempDir
    Path projectPath;

    @Test
    void failedGateCancelsAndAbortsThePendingMerge() throws Exception {
        configure(SonarQubeTrigger.MERGES, SonarQubeFailurePolicy.CANCEL);
        RecordingMergeExecutor merges = new RecordingMergeExecutor();
        RecordingConsole console = new RecordingConsole();

        int exitCode = new CommandLine(command(merges, console)).execute("feature/unsafe");

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        assertTrue(merges.began);
        assertTrue(merges.aborted);
        assertFalse(merges.completed);
        assertTrue(console.errors().contains("Git merge was aborted"));
    }

    @Test
    void userCanApproveAFailedGateAndCompleteTheMerge() throws Exception {
        configure(SonarQubeTrigger.MERGES, SonarQubeFailurePolicy.ASK_FOR_APPROVAL);
        RecordingMergeExecutor merges = new RecordingMergeExecutor();
        RecordingConsole console = new RecordingConsole("y");

        int exitCode = new CommandLine(command(merges, console)).execute("feature/reviewed");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(merges.began);
        assertTrue(merges.completed);
        assertFalse(merges.aborted);
        assertTrue(console.output().contains("new_coverage: 70"));
    }

    @Test
    void commitOnlyConfigurationLeavesMergeBehaviorNative() throws Exception {
        configure(SonarQubeTrigger.COMMITS, SonarQubeFailurePolicy.CANCEL);
        RecordingMergeExecutor merges = new RecordingMergeExecutor();

        int exitCode = new CommandLine(command(merges, new RecordingConsole())).execute("feature/fast");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(merges.mergedDirectly);
        assertFalse(merges.began);
    }

    private MergeCommand command(RecordingMergeExecutor merges, RecordingConsole console) {
        SonarQubeService service = new SonarQubeService(new SonarQubeServiceDependencies(
                new BetterGitConfigurationLoader(), new SonarQubeConfigurationLoader(), request -> {
                    Files.createDirectories(request.metadataFile().getParent());
                    Files.writeString(request.metadataFile(), "dashboardUrl=https://sonar.example/project\n");
                    return 1;
                }, configuration -> new SonarQualityGate(
                        "ERROR", List.of(new SonarCondition("new_coverage", "ERROR", "70", "80"))),
                Map.of(SonarQubeConfigurationLoader.TOKEN, "secret-token")));
        return new MergeCommand(
                new MergeCommandDependencies(merges, path -> "main", new SonarQubeGate(service, console), console),
                projectPath);
    }

    private void configure(SonarQubeTrigger trigger, SonarQubeFailurePolicy policy) throws Exception {
        SonarQubeSettings sonar = new SonarQubeSettings(
                "https://sonar.example", "bettergit", trigger, List.of("main"), policy);
        BetterGitConfiguration configuration = new BetterGitConfiguration(
                3, "2026-08-29T12:00:00Z", projectPath.toString(), true, true,
                new FeatureSettings(false, false, true, sonar), false, AiCommitSettings.disabled(null));
        new BetterGitFileStore().writeInitialization(projectPath, configuration, "# Project\n");
    }

    private static final class RecordingMergeExecutor implements GitMergeExecutor {
        private boolean mergedDirectly;
        private boolean began;
        private boolean completed;
        private boolean aborted;

        @Override
        public int merge(Path projectPath, String sourceBranch, String message) {
            mergedDirectly = true;
            return 0;
        }

        @Override
        public int beginGatedMerge(Path projectPath, String sourceBranch) {
            began = true;
            return 0;
        }

        @Override
        public int completeGatedMerge(Path projectPath, String message) {
            completed = true;
            return 0;
        }

        @Override
        public boolean hasPendingMerge(Path projectPath) {
            return true;
        }

        @Override
        public void abortGatedMerge(Path projectPath) throws IOException {
            aborted = true;
        }
    }
}
