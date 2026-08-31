package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.documentation.AiTextGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCommitReportGeneratorTest {
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final AiConfiguration COMPLETE_AI = new AiConfiguration(
            "secret-api-key", "test-model", "https://ai.example/{model}");

    @TempDir
    Path projectPath;

    @Test
    void disabledFeatureDoesNotReadGitOrCallAi() throws Exception {
        RecordingCommitSource source = new RecordingCommitSource(snapshot("+safe"));
        RecordingAi ai = new RecordingAi(validReport());

        CommitReportOutcome outcome = generator(source, ai).generate(request(
                AiCommitSettings.disabled("test-model"), COMPLETE_AI, CommitReportLimits.DEFAULT_MAXIMUM));

        assertEquals(CommitReportStatus.DISABLED, outcome.status());
        assertEquals(0, source.calls);
        assertEquals(0, ai.calls);
    }

    @Test
    void missingAiKeyOrModelSkipsWithoutReadingGit() throws Exception {
        RecordingCommitSource source = new RecordingCommitSource(snapshot("+safe"));
        RecordingAi ai = new RecordingAi(validReport());
        List<AiConfiguration> incompleteConfigurations = List.of(
                new AiConfiguration(null, "test-model", "https://ai.example/{model}"),
                new AiConfiguration("secret-api-key", null, "https://ai.example/{model}"));

        for (AiConfiguration configuration : incompleteConfigurations) {
            CommitReportOutcome outcome = generator(source, ai).generate(request(
                    enabledSettings(false), configuration, CommitReportLimits.DEFAULT_MAXIMUM));
            assertEquals(CommitReportStatus.AI_NOT_CONFIGURED, outcome.status());
        }
        assertEquals(0, source.calls);
        assertEquals(0, ai.calls);
    }

    @Test
    void enabledFeatureComposesDiffMemoryAndSelectiveJavaContext() throws Exception {
        Path javaFile = projectPath.resolve("src/main/java/example/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "package example;\nimport java.util.List;\npublic class App {\n void run() {}\n}\n");
        writeMemory("# General\nCompact project purpose.", "## old-hash · 2026-08-14T12:00:00Z\n**Title:** feat: old");
        String diff = javaDiff("src/main/java/example/App.java", "+ void run() {}");
        RecordingAi ai = new RecordingAi(validReport());

        CommitReportOutcome outcome = generator(new RecordingCommitSource(snapshot(
                        diff, "src/main/java/example/App.java")), ai)
                .generate(request(enabledSettings(true), COMPLETE_AI, CommitReportLimits.DEFAULT_MAXIMUM));

        assertEquals(CommitReportStatus.GENERATED, outcome.status());
        assertTrue(ai.prompt.contains("Compact project purpose"));
        assertTrue(ai.prompt.contains("feat: old"));
        assertTrue(ai.prompt.contains("+ void run() {}"));
        assertTrue(ai.prompt.contains("package example"));
        assertTrue(ai.prompt.contains("- MODIFIED src/main/java/example/App.java"));
    }

    @Test
    void missingMemoryFilesFallBackToCurrentDiff() throws Exception {
        RecordingAi ai = new RecordingAi(validReport());

        CommitReportOutcome outcome = generator(new RecordingCommitSource(snapshot("+safe change")), ai)
                .generate(request(enabledSettings(true), COMPLETE_AI, CommitReportLimits.DEFAULT_MAXIMUM));

        assertEquals(CommitReportStatus.GENERATED, outcome.status());
        assertTrue(ai.prompt.contains("+safe change"));
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/general.md")));
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/context/project-map.json")));
    }

    @Test
    void secretsAndSensitiveFilesAreExcludedFromPromptAndReport() throws Exception {
        Path javaFile = projectPath.resolve("src/App.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class App { String password=\"source secret with spaces\"; }\n");
        String diff = """
                diff --git a/.env b/.env
                --- a/.env
                +++ b/.env
                +AI_API_KEY=env-secret
                diff --git a/src/App.java b/src/App.java
                --- a/src/App.java
                +++ b/src/App.java
                +String apiKey=secret-api-key;
                +String password="a secret with spaces";
                """;
        RecordingAi ai = new RecordingAi(
                validReport().replace("Validation was not run or provided.", "secret-api-key"));

        CommitReportOutcome outcome = generator(new RecordingCommitSource(snapshot(diff)), ai)
                .generate(request(enabledSettings(false), COMPLETE_AI, CommitReportLimits.DEFAULT_MAXIMUM));

        assertEquals(CommitReportStatus.GENERATED, outcome.status());
        assertFalse(ai.prompt.contains("env-secret"));
        assertFalse(ai.prompt.contains("secret-api-key"));
        assertFalse(ai.prompt.contains("a secret with spaces"));
        assertFalse(ai.prompt.contains("source secret with spaces"));
        assertFalse(Files.readString(outcome.reportPath()).contains("secret-api-key"));
        assertTrue(Files.readString(outcome.reportPath()).contains("[REDACTED]"));
    }

    @Test
    void oversizedInputIsTrimmedWithAnExplicitMarker() throws Exception {
        RecordingAi ai = new RecordingAi(validReport());
        String largeDiff = "+changed-line\n".repeat(2_000);

        CommitReportOutcome outcome = generator(new RecordingCommitSource(snapshot(largeDiff)), ai)
                .generate(request(enabledSettings(false), COMPLETE_AI, 1_000));

        assertEquals(CommitReportStatus.GENERATED, outcome.status());
        assertEquals(1_000, ai.prompt.length());
        assertTrue(ai.prompt.endsWith("[TRUNCATED BY BETTERGIT]\n"));
        assertTrue(outcome.inputTruncated());
    }

    @Test
    void recentHistoryKeepsOnlyTheLatestTenEntries() throws Exception {
        AiMemoryStore memoryStore = memoryStore();
        memoryStore.initialize(projectPath);
        for (int index = 0; index < 12; index++) {
            memoryStore.appendHistory(projectPath, new HistoryEntry(
                    "hash-" + index, NOW.plusSeconds(index), "feat: change " + index,
                    List.of("src/Area" + index), "Summary " + index));
        }

        String history = memoryStore.read(projectPath, 10).recentHistory();

        assertEquals(10, history.lines().filter(line -> line.startsWith("## ")).count());
        assertTrue(history.contains("hash-11"));
        assertFalse(history.contains("## hash-0 ·"));
        assertFalse(history.contains("## hash-1 ·"));
    }

    @Test
    void successfulReportIsSavedAndAiFailureIsSafelySkipped() throws Exception {
        CommitReportOutcome successful = generator(
                new RecordingCommitSource(snapshot("+safe")), new RecordingAi(validReport()))
                .generate(request(enabledSettings(false), COMPLETE_AI, CommitReportLimits.DEFAULT_MAXIMUM));
        RecordingAi failingAi = new RecordingAi(new IOException("provider failed with secret-api-key"));
        CommitReportOutcome failed = generator(new RecordingCommitSource(snapshot("+safe")), failingAi)
                .generate(request(enabledSettings(false), COMPLETE_AI, CommitReportLimits.DEFAULT_MAXIMUM));

        assertEquals(CommitReportStatus.GENERATED, successful.status());
        assertTrue(successful.reportPath().getFileName().toString().startsWith("pending-"));
        assertTrue(Files.readString(successful.reportPath())
                .startsWith("Adds concise documentation for staged changes.\n\n## Changes"));
        assertEquals("Adds concise documentation for staged changes.", successful.commitMessage());
        assertEquals(CommitReportStatus.AI_REQUEST_FAILED, failed.status());
        assertTrue(Files.isRegularFile(failed.diagnosticPath()));
        String diagnostic = Files.readString(failed.diagnosticPath());
        assertTrue(diagnostic.contains("provider failed with [REDACTED]"));
        assertFalse(diagnostic.contains("secret-api-key"));
    }

    @Test
    void invalidAiResponseIsSavedWithTheValidationReason() throws Exception {
        String invalidResponse = "# Unexpected title\n\nsecret-api-key";

        CommitReportOutcome outcome = generator(
                new RecordingCommitSource(snapshot("+safe")), new RecordingAi(invalidResponse))
                .generate(request(enabledSettings(false), COMPLETE_AI, CommitReportLimits.DEFAULT_MAXIMUM));

        assertEquals(CommitReportStatus.INVALID_AI_RESPONSE, outcome.status());
        assertTrue(Files.isRegularFile(outcome.diagnosticPath()));
        assertTrue(outcome.diagnosticPath().startsWith(projectPath.resolve(".bettergit/reports/errors")));
        String diagnostic = Files.readString(outcome.diagnosticPath());
        assertTrue(diagnostic.contains("missing required Markdown headings"));
        assertTrue(diagnostic.contains("# Unexpected title"));
        assertTrue(diagnostic.contains("[REDACTED]"));
        assertFalse(diagnostic.contains("secret-api-key"));
    }

    private AiCommitReportGenerator generator(CommitDataSource source, AiTextGenerator ai) {
        SensitiveContentFilter filter = new SensitiveContentFilter();
        return new AiCommitReportGenerator(new CommitReportDependencies(
                source,
                ai,
                memoryStore(),
                new AiCommitContextBuilder(filter, new JavaSourceContextCollector()),
                new AiCommitPromptBuilder(),
                filter,
                new CommitReportValidator(),
                new CommitReportStore(),
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private AiMemoryStore memoryStore() {
        return new AiMemoryStore(new ProjectMapScanner());
    }

    private CommitReportRequest request(
            AiCommitSettings settings, AiConfiguration ai, int maximumCharacters) {
        return new CommitReportRequest(
                projectPath,
                new BetterGitConfiguration(2, NOW.toString(), projectPath.toString(), true, true,
                        FeatureSettings.disabled(), true, settings),
                ai,
                new CommitReportLimits(maximumCharacters));
    }

    private static AiCommitSettings enabledSettings(boolean memoryEnabled) {
        return new AiCommitSettings(true, memoryEnabled, "test-model");
    }

    private static CommitSnapshot snapshot(String diff) {
        return snapshot(diff, "src/App.java");
    }

    private static CommitSnapshot snapshot(String diff, String changedPath) {
        return new CommitSnapshot(
                "feature/reports",
                List.of(ChangedFile.modified(changedPath)),
                diff,
                new DiffStatistics(1, 1, 0),
                "Validation was not run or provided.");
    }

    private static String javaDiff(String path, String changedLine) {
        return "diff --git a/%1$s b/%1$s\n--- a/%1$s\n+++ b/%1$s\n@@ -1,4 +1,5 @@\n%2$s\n"
                .formatted(path, changedLine);
    }

    private void writeMemory(String general, String history) throws Exception {
        Path context = projectPath.resolve(".bettergit/context");
        Files.createDirectories(context);
        Files.writeString(projectPath.resolve(".bettergit/general.md"), general);
        Files.writeString(context.resolve("recent-history.md"), history);
    }

    private static String validReport() {
        return """
                Adds concise documentation for staged changes.

                ## Changes
                - src/App.java: report flow

                ## Validation
                Validation was not run or provided.
                """;
    }

    private static final class RecordingCommitSource implements CommitDataSource {
        private final CommitSnapshot snapshot;
        private int calls;

        private RecordingCommitSource(CommitSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public CommitSnapshot stagedSnapshot(Path projectPath) {
            calls++;
            return snapshot;
        }
    }

    private static final class RecordingAi implements AiTextGenerator {
        private final String response;
        private final IOException failure;
        private int calls;
        private String prompt;

        private RecordingAi(String response) {
            this.response = response;
            this.failure = null;
        }

        private RecordingAi(IOException failure) {
            this.response = null;
            this.failure = failure;
        }

        @Override
        public String generate(AiConfiguration configuration, String prompt) throws IOException {
            calls++;
            this.prompt = prompt;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
