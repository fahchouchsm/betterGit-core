package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commitreport.AiCommitContextBuilder;
import io.fahchouchsm.betterGitCore.commitreport.AiCommitPromptBuilder;
import io.fahchouchsm.betterGitCore.commitreport.AiCommitReportGenerator;
import io.fahchouchsm.betterGitCore.commitreport.AiMemoryStore;
import io.fahchouchsm.betterGitCore.commitreport.ChangedFile;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportDependencies;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportStore;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportValidator;
import io.fahchouchsm.betterGitCore.commitreport.CommitSnapshot;
import io.fahchouchsm.betterGitCore.commitreport.DiffStatistics;
import io.fahchouchsm.betterGitCore.commitreport.JavaSourceContextCollector;
import io.fahchouchsm.betterGitCore.commitreport.ProjectMapScanner;
import io.fahchouchsm.betterGitCore.commitreport.SensitiveContentFilter;
import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.documentation.AiTextGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitCommandTest {
    private static final String COMMIT_HASH = "1234567890123456789012345678901234567890";
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @TempDir
    Path projectPath;

    @Test
    void aiSuggestionCreatesCommitAndFinalizesReportAndHistory() throws Exception {
        writeConfiguration(true);
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole();
        CommitCommand command = command(validReportGenerator(), commits, console);

        int exitCode = new CommandLine(command).execute();

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertEquals("feat(log): add modern history", commits.message);
        Path report = projectPath.resolve(".bettergit/reports/" + COMMIT_HASH + ".md");
        assertTrue(Files.isRegularFile(report));
        assertFalse(Files.list(report.getParent())
                .anyMatch(path -> path.getFileName().toString().startsWith("pending-")));
        assertTrue(Files.readString(projectPath.resolve(".bettergit/context/recent-history.md"))
                .contains("## " + COMMIT_HASH + " ·"));
        assertTrue(console.output().contains("Committed 12345678"));
    }

    @Test
    void providerFailureDoesNotBlockExplicitCommitMessage() throws Exception {
        writeConfiguration(false);
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole();
        AiTextGenerator failingAi = (configuration, prompt) -> {
            throw new IOException("provider unavailable with secret-api-key");
        };
        CommitCommand command = command(failingAi, commits, console);

        int exitCode = new CommandLine(command).execute("-m", "fix: explicit fallback");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertEquals("fix: explicit fallback", commits.message);
        assertTrue(console.output().contains("provider request failed"));
        assertFalse(console.output().contains("secret-api-key"));
    }

    private CommitCommand command(
            AiTextGenerator ai, RecordingCommitExecutor commits, RecordingConsole console) {
        BetterGitFileStore fileStore = new BetterGitFileStore();
        AiMemoryStore memoryStore = new AiMemoryStore(new ProjectMapScanner());
        CommitReportStore reportStore = new CommitReportStore();
        SensitiveContentFilter filter = new SensitiveContentFilter();
        AiCommitReportGenerator generator = new AiCommitReportGenerator(new CommitReportDependencies(
                project -> snapshot(), ai, memoryStore,
                new AiCommitContextBuilder(filter, new JavaSourceContextCollector()),
                new AiCommitPromptBuilder(), filter, new CommitReportValidator(), reportStore,
                Clock.fixed(NOW, ZoneOffset.UTC)));
        CommitCommandDependencies dependencies = new CommitCommandDependencies(
                commits, generator, reportStore, memoryStore, new BetterGitConfigurationLoader(),
                new AiConfigurationLoader(), fileStore, console, Map.of(
                        "AI_API_KEY", "secret-api-key",
                        "AI_API_MODEL", "test-model",
                        "AI_API_URL", "https://ai.example/{model}"));
        return new CommitCommand(dependencies, projectPath);
    }

    private void writeConfiguration(boolean memoryEnabled) throws Exception {
        BetterGitConfiguration configuration = new BetterGitConfiguration(
                2, NOW.toString(), projectPath.toString(), true, true,
                FeatureSettings.disabled(), true,
                new AiCommitSettings(true, memoryEnabled, "test-model"));
        new BetterGitFileStore().writeInitialization(projectPath, configuration, "# Project\n");
    }

    private static CommitSnapshot snapshot() {
        String diff = "diff --git a/src/App.java b/src/App.java\n+safe change\n";
        return new CommitSnapshot("main", List.of(ChangedFile.modified("src/App.java")),
                diff, new DiffStatistics(1, 1, 0), "Validation was not run or provided.");
    }

    private static AiTextGenerator validReportGenerator() {
        return (configuration, prompt) -> """
                # Commit Report

                ## Suggested commit message
                feat(log): add modern history

                ## Summary
                Adds a modern history view.

                ## Changed areas
                - history: rich log output

                ## Technical details
                - Reads commit metadata with JGit.

                ## Validation
                - Tests passed.

                ## Risks or follow-up
                - None identified.
                """;
    }

    private static final class RecordingCommitExecutor implements CommitExecutor {
        private String message;

        @Override
        public String commit(Path projectPath, String message) {
            this.message = message;
            return COMMIT_HASH;
        }
    }
}
