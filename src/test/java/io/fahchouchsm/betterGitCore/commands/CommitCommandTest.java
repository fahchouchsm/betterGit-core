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
import io.fahchouchsm.betterGitCore.configuration.AiCredentialStore;
import io.fahchouchsm.betterGitCore.configuration.AiSetupService;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.documentation.AiTextGenerator;
import io.fahchouchsm.betterGitCore.diagram.ClassDiagramGenerator;
import io.fahchouchsm.betterGitCore.diagram.CommitDiagramService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitCommandTest {
    private static final String COMMIT_HASH = "1234567890123456789012345678901234567890";
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @TempDir
    Path projectPath;

    private CommitSnapshot commitSnapshot = snapshot();
    private ClassDiagramGenerator diagramGenerator =
            (source, output) -> Files.writeString(output, "<svg/>\n");

    @Test
    void aiSuggestionCreatesCommitAndFinalizesReportAndHistory() throws Exception {
        writeConfigurationWithMemory();
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole();
        CommitCommand command = command(validReportGenerator(), commits, console);

        int exitCode = new CommandLine(command).execute("-m", "this must not replace the report description");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertEquals("Adds a modern history view for repository commits.", commits.message);
        Path report = projectPath.resolve(".bettergit/reports/" + COMMIT_HASH + ".md");
        assertTrue(Files.isRegularFile(report));
        assertTrue(Files.readString(report).startsWith(commits.message + "\n\n## Changes"));
        assertFalse(Files.list(report.getParent())
                .anyMatch(path -> path.getFileName().toString().startsWith("pending-")));
        assertTrue(Files.readString(projectPath.resolve(".bettergit/context/recent-history.md"))
                .contains("## " + COMMIT_HASH + " ·"));
        assertTrue(console.output().contains("Committed 12345678"));
    }

    @Test
    void providerFailureCancelsAnAiDocumentedCommit() throws Exception {
        writeConfigurationWithoutMemory();
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole();
        AiTextGenerator failingAi = (configuration, prompt) -> {
            throw new IOException("provider unavailable with secret-api-key");
        };
        CommitCommand command = command(failingAi, commits, console);

        int exitCode = new CommandLine(command).execute("-m", "fix: explicit fallback");

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
        assertNull(commits.message);
        assertTrue(console.output().contains("provider request failed"));
        assertTrue(console.output().contains("AI failure details saved:"));
        assertTrue(console.errors().contains("Commit cancelled"));
        assertFalse(console.output().contains("secret-api-key"));
        try (var diagnostics = Files.list(projectPath.resolve(".bettergit/reports/errors"))) {
            assertEquals(1, diagnostics.count());
        }
    }

    @Test
    void missingApiKeyStartsGuidedSetupAndStoresTheKey() throws Exception {
        writeConfigurationWithoutMemory();
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole("y", "", "stored-key", "", "");
        Map<String, String> settingsWithoutKey = Map.of(
                "AI_API_MODEL", "test-model",
                "AI_API_URL", "https://ai.example/{model}");
        CommitCommand command = command(
                validReportGenerator(), commits, console, settingsWithoutKey);

        int exitCode = new CommandLine(command).execute();

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertEquals("Adds a modern history view for repository commits.", commits.message);
        assertTrue(Files.readString(projectPath.resolve(".env")).contains("AI_API_KEY=stored-key"));
        assertFalse(console.output().contains("stored-key"));
    }

    @Test
    void missingProjectConfigurationNamesTheTargetDirectory() {
        RecordingConsole console = new RecordingConsole();
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        CommitCommand command = command(validReportGenerator(), commits, console);

        int exitCode = new CommandLine(command).execute();

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        assertNull(commits.message);
        assertTrue(console.output().contains("No BetterGit configuration found in " + projectPath));
        assertTrue(console.output().contains("pass an initialized directory"));
    }

    @Test
    void noStagedChangesIsASuccessfulInformationalNoOp() throws Exception {
        writeConfigurationWithoutMemory();
        commitSnapshot = new CommitSnapshot(
                "main", List.of(), "", new DiffStatistics(0, 0, 0), "Validation was not run or provided.");
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole();
        CommitCommand command = command(validReportGenerator(), commits, console);

        int exitCode = new CommandLine(command).execute();

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertNull(commits.message);
        assertTrue(console.output().contains("Nothing to commit"));
        assertTrue(console.output().contains("non-ignored file"));
        assertTrue(console.errors().isEmpty());
    }

    @Test
    void enabledClassDiagramIsSavedForTheSuccessfulCommit() throws Exception {
        writeConfigurationWithDiagrams();
        Files.createDirectories(projectPath.resolve("src/main/java"));
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole();
        CommitCommand command = command(validReportGenerator(), commits, console);

        int exitCode = new CommandLine(command).execute();

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        Path diagram = projectPath.resolve(".bettergit/diagrams/" + COMMIT_HASH + ".svg");
        assertTrue(Files.isRegularFile(diagram));
        assertTrue(console.output().contains("Class diagram saved: " + diagram));
    }

    @Test
    void diagramFailureDoesNotMisreportTheSuccessfulCommit() throws Exception {
        writeConfigurationWithDiagrams();
        Files.createDirectories(projectPath.resolve("src/main/java"));
        diagramGenerator = (source, output) -> {
            throw new IOException("renderer failed");
        };
        RecordingCommitExecutor commits = new RecordingCommitExecutor();
        RecordingConsole console = new RecordingConsole();
        CommitCommand command = command(validReportGenerator(), commits, console);

        int exitCode = new CommandLine(command).execute();

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertEquals("Adds a modern history view for repository commits.", commits.message);
        assertTrue(console.output().contains("Commit succeeded, but its class diagram could not be generated"));
        assertTrue(console.output().contains("renderer failed"));
    }

    private CommitCommand command(
            AiTextGenerator ai, RecordingCommitExecutor commits, RecordingConsole console) {
        return command(ai, commits, console, Map.of(
                "AI_API_KEY", "secret-api-key",
                "AI_API_MODEL", "test-model",
                "AI_API_URL", "https://ai.example/{model}"));
    }

    private CommitCommand command(
            AiTextGenerator ai,
            RecordingCommitExecutor commits,
            RecordingConsole console,
            Map<String, String> environment) {
        BetterGitFileStore fileStore = new BetterGitFileStore();
        AiMemoryStore memoryStore = new AiMemoryStore(new ProjectMapScanner());
        CommitReportStore reportStore = new CommitReportStore();
        SensitiveContentFilter filter = new SensitiveContentFilter();
        AiCommitReportGenerator generator = new AiCommitReportGenerator(new CommitReportDependencies(
                project -> commitSnapshot, ai, memoryStore,
                new AiCommitContextBuilder(filter, new JavaSourceContextCollector()),
                new AiCommitPromptBuilder(), filter, new CommitReportValidator(), reportStore,
                Clock.fixed(NOW, ZoneOffset.UTC)));
        CommitCommandDependencies dependencies = new CommitCommandDependencies(
                commits, generator,
                new CommitDiagramService(diagramGenerator,
                        new BetterGitConfigurationLoader(), fileStore),
                reportStore, memoryStore, new BetterGitConfigurationLoader(),
                new AiConfigurationLoader(), new AiSetupService(fileStore, new AiCredentialStore()),
                fileStore, console, environment);
        return new CommitCommand(dependencies, projectPath);
    }

    private void writeConfigurationWithMemory() throws Exception {
        writeConfiguration(
                FeatureSettings.disabled(), new AiCommitSettings(true, true, "test-model"));
    }

    private void writeConfigurationWithoutMemory() throws Exception {
        writeConfiguration(
                FeatureSettings.disabled(), new AiCommitSettings(true, false, "test-model"));
    }

    private void writeConfigurationWithDiagrams() throws Exception {
        writeConfiguration(
                new FeatureSettings(true, false, false),
                new AiCommitSettings(true, false, "test-model"));
    }

    private void writeConfiguration(FeatureSettings features, AiCommitSettings aiSettings) throws Exception {
        BetterGitConfiguration configuration = new BetterGitConfiguration(
                2, NOW.toString(), projectPath.toString(), true, true,
                features, true, aiSettings);
        new BetterGitFileStore().writeInitialization(projectPath, configuration, "# Project\n");
    }

    private static CommitSnapshot snapshot() {
        String diff = "diff --git a/src/App.java b/src/App.java\n+safe change\n";
        return new CommitSnapshot("main", List.of(ChangedFile.modified("src/App.java")),
                diff, new DiffStatistics(1, 1, 0), "Validation was not run or provided.");
    }

    private static AiTextGenerator validReportGenerator() {
        return (configuration, prompt) -> """
                Adds a modern history view for repository commits.

                ## Changes
                - history: rich log output

                ## Validation
                Tests passed.
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
