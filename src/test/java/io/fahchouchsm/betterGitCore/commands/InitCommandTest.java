package io.fahchouchsm.betterGitCore.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.documentation.AiTextGenerator;
import io.fahchouchsm.betterGitCore.documentation.ProjectDocumentationGenerator;
import io.fahchouchsm.betterGitCore.project.JavaProjectDetector;
import io.fahchouchsm.betterGitCore.project.MarkdownProjectScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitCommandTest {
    private static final Map<String, String> COMPLETE_AI_ENVIRONMENT = Map.of(
            "AI_API_KEY", "secret-api-key",
            "AI_API_MODEL", "test-model",
            "AI_API_URL", "https://ai.example/generate");

    @TempDir
    Path projectPath;

    @Test
    void existingGitRepositoryIsNeverInitialized() throws Exception {
        createJavaProject();
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(true);
        int exitCode = command(repository, new RecordingConsole("", "", ""), Map.of(), successfulAi()).execute(projectPath);

        assertEquals(InitCommand.SUCCESS, exitCode);
        assertEquals(0, repository.initializationCount);
        assertTrue(configuration().get("gitAlreadyExisted").getAsBoolean());
    }

    @Test
    void initializesGitOnlyAfterSetupFilesExist() {
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(false);
        repository.beforeInitialization = initializedPath -> {
            assertTrue(Files.isRegularFile(initializedPath.resolve(".bettergit/config.json")));
            assertTrue(Files.isRegularFile(initializedPath.resolve(".bettergit/general.md")));
        };

        int exitCode = command(repository, new RecordingConsole(), Map.of(), successfulAi()).execute(projectPath);

        assertEquals(InitCommand.SUCCESS, exitCode);
        assertEquals(1, repository.initializationCount);
    }

    @Test
    void nonJavaProjectUsesLimitedModeWithoutQuestions() throws Exception {
        RecordingConsole console = new RecordingConsole();

        int exitCode = command(new RecordingRepositoryAccess(true), console, Map.of(), successfulAi())
                .execute(projectPath);

        assertEquals(InitCommand.SUCCESS, exitCode);
        assertTrue(console.output().contains("limited mode"));
        assertTrue(console.prompts.isEmpty());
        assertEquals("LIMITED", configuration().get("projectType").getAsString());
    }

    @Test
    void persistsJavaFeatureSettings() throws Exception {
        createJavaProject();

        int exitCode = command(
                new RecordingRepositoryAccess(true), new RecordingConsole("y", "n", "yes"), Map.of(), successfulAi())
                .execute(projectPath);

        JsonObject settings = configuration().getAsJsonObject("settings");
        assertEquals(InitCommand.SUCCESS, exitCode);
        assertTrue(settings.get("classDiagramOnCommit").getAsBoolean());
        assertFalse(settings.get("testDurationTracking").getAsBoolean());
        assertTrue(settings.get("sonarQubeDocumentation").getAsBoolean());
        assertEquals("2026-08-13T12:00:00Z", configuration().get("initializedAt").getAsString());
    }

    @Test
    void writesGeneratedAiDocumentation() throws Exception {
        Files.writeString(projectPath.resolve("README.md"), "# Sample\nRun with Maven.");
        AiTextGenerator generator = (configuration, prompt) -> {
            assertTrue(prompt.contains("Run with Maven"));
            return "# Generated overview\n";
        };

        int exitCode = command(
                new RecordingRepositoryAccess(true), new RecordingConsole(), COMPLETE_AI_ENVIRONMENT, generator)
                .execute(projectPath);

        assertEquals(InitCommand.SUCCESS, exitCode);
        assertEquals("# Generated overview\n", Files.readString(projectPath.resolve(".bettergit/general.md")));
        assertTrue(configuration().get("aiDocumentationAvailable").getAsBoolean());
    }

    @Test
    void aiFailureCreatesPlaceholderAndInitializationSucceeds() throws Exception {
        Files.writeString(projectPath.resolve("README.md"), "# Sample");
        AiTextGenerator failingGenerator = (configuration, prompt) -> {
            throw new IOException("provider unavailable");
        };
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(false);

        int exitCode = command(repository, new RecordingConsole(), COMPLETE_AI_ENVIRONMENT, failingGenerator)
                .execute(projectPath);

        String documentation = Files.readString(projectPath.resolve(".bettergit/general.md"));
        assertEquals(InitCommand.SUCCESS, exitCode);
        assertEquals(1, repository.initializationCount);
        assertTrue(documentation.contains("AI request failed"));
        assertFalse(documentation.contains("provider unavailable"));
    }

    @Test
    void apiKeyIsNeverPersisted() throws Exception {
        Files.writeString(projectPath.resolve("README.md"), "# Sample");
        AiTextGenerator leakingGenerator = (configuration, prompt) -> "Accidental " + configuration.apiKey();
        RecordingConsole console = new RecordingConsole();

        int exitCode = command(
                new RecordingRepositoryAccess(true), console, COMPLETE_AI_ENVIRONMENT, leakingGenerator)
                .execute(projectPath);

        String configuration = Files.readString(projectPath.resolve(".bettergit/config.json"));
        String documentation = Files.readString(projectPath.resolve(".bettergit/general.md"));
        assertEquals(InitCommand.SUCCESS, exitCode);
        assertFalse(configuration.contains("secret-api-key"));
        assertFalse(documentation.contains("secret-api-key"));
        assertFalse(console.output().contains("secret-api-key"));
        assertTrue(documentation.contains("[REDACTED]"));
    }

    @Test
    void invalidInteractiveAnswersAreRejectedAndAskedAgain() throws Exception {
        createJavaProject();
        RecordingConsole console = new RecordingConsole("maybe", "y", "later", "", "YES");

        int exitCode = command(new RecordingRepositoryAccess(true), console, Map.of(), successfulAi())
                .execute(projectPath);

        JsonObject settings = configuration().getAsJsonObject("settings");
        assertEquals(InitCommand.SUCCESS, exitCode);
        assertEquals(5, console.prompts.size());
        assertTrue(settings.get("classDiagramOnCommit").getAsBoolean());
        assertFalse(settings.get("testDurationTracking").getAsBoolean());
        assertTrue(settings.get("sonarQubeDocumentation").getAsBoolean());
    }

    @Test
    void preservesGitIgnoreContentWhenAddingEnv() throws Exception {
        Files.writeString(projectPath.resolve(".env"), "AI_API_KEY=local-key\n");
        Files.writeString(projectPath.resolve(".gitignore"), "target/\n*.log\n");

        int exitCode = command(new RecordingRepositoryAccess(false), new RecordingConsole(), Map.of(), successfulAi())
                .execute(projectPath);

        String gitIgnore = Files.readString(projectPath.resolve(".gitignore"));
        assertEquals(InitCommand.SUCCESS, exitCode);
        assertTrue(gitIgnore.contains("target/\n*.log\n"));
        assertTrue(Arrays.asList(gitIgnore.split("\\R")).contains(".env"));
    }

    private InitCommand command(
            RecordingRepositoryAccess repository,
            RecordingConsole console,
            Map<String, String> environment,
            AiTextGenerator aiTextGenerator) {
        return new InitCommand(new InitCommandDependencies(
                repository,
                console,
                new AiConfigurationLoader(),
                new JavaProjectDetector(),
                new MarkdownProjectScanner(),
                new ProjectDocumentationGenerator(aiTextGenerator),
                new BetterGitFileStore(),
                environment,
                Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC)));
    }

    private JsonObject configuration() throws Exception {
        return JsonParser.parseString(Files.readString(projectPath.resolve(".bettergit/config.json"))).getAsJsonObject();
    }

    private void createJavaProject() throws Exception {
        Files.writeString(projectPath.resolve("pom.xml"), "<project/>");
    }

    private AiTextGenerator successfulAi() {
        return (configuration, prompt) -> "# Generated";
    }

    private static final class RecordingRepositoryAccess implements RepositoryAccess {
        private final boolean repositoryExists;
        private int initializationCount;
        private Consumer<Path> beforeInitialization = ignored -> { };

        private RecordingRepositoryAccess(boolean repositoryExists) {
            this.repositoryExists = repositoryExists;
        }

        @Override
        public boolean isInsideRepository(Path ignored) {
            return repositoryExists;
        }

        @Override
        public void initialize(Path projectPath) {
            beforeInitialization.accept(projectPath);
            initializationCount++;
        }
    }

    private static final class RecordingConsole implements CommandConsole {
        private final Queue<String> answers;
        private final List<String> messages = new ArrayList<>();
        private final List<String> prompts = new ArrayList<>();

        private RecordingConsole(String... answers) {
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public String readLine(String prompt) {
            prompts.add(prompt);
            return answers.isEmpty() ? "" : answers.remove();
        }

        @Override
        public void println(String message) {
            messages.add(message);
        }

        private String output() {
            return String.join("\n", messages);
        }
    }
}
