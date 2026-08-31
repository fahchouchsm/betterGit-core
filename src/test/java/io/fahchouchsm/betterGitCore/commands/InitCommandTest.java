package io.fahchouchsm.betterGitCore.commands;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fahchouchsm.betterGitCore.commands.init.BetterGitInitializer;
import io.fahchouchsm.betterGitCore.commands.init.InitConfiguration;
import io.fahchouchsm.betterGitCore.commands.init.InitializationDependencies;
import io.fahchouchsm.betterGitCore.commands.init.InitializationMode;
import io.fahchouchsm.betterGitCore.commitreport.AiMemoryStore;
import io.fahchouchsm.betterGitCore.commitreport.ProjectMapScanner;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.AiCredentialStore;
import io.fahchouchsm.betterGitCore.configuration.AiSetupService;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        initialize(repository, new RecordingConsole("", "", ""), Map.of(), successfulAi(), false);

        assertEquals(0, repository.initializationCount);
        assertTrue(configuration().get("gitAlreadyExisted").getAsBoolean());
    }

    @Test
    void initializesGitOnlyAfterEverySetupFileExists() throws Exception {
        Files.writeString(projectPath.resolve(".env"), "AI_API_KEY=local-key\n");
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(false);
        repository.beforeInitialization = initializedPath -> {
            assertTrue(Files.isRegularFile(initializedPath.resolve(".bettergit/config.json")));
            assertTrue(Files.isRegularFile(initializedPath.resolve(".bettergit/general.md")));
            assertTrue(readUnchecked(initializedPath.resolve(".gitignore")).contains(".env"));
        };

        initialize(repository, new RecordingConsole(), Map.of(), successfulAi(), false);

        assertEquals(1, repository.initializationCount);
    }

    @Test
    void setupFailurePreventsGitInitialization() throws Exception {
        Files.writeString(projectPath.resolve(".bettergit"), "blocks directory creation");
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(false);

        assertThrows(IOException.class, () ->
                initialize(repository, new RecordingConsole(), Map.of(), successfulAi(), false));

        assertEquals(0, repository.initializationCount);
    }

    @Test
    void symbolicBetterGitDirectoryIsRejectedWithoutInitializingGit() throws Exception {
        Path redirectedDirectory = Files.createDirectory(projectPath.resolve("redirected"));
        Files.createSymbolicLink(projectPath.resolve(".bettergit"), redirectedDirectory);
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(false);

        assertThrows(IOException.class, () ->
                initialize(repository, new RecordingConsole(), Map.of(), successfulAi(), false));

        assertEquals(0, repository.initializationCount);
        assertFalse(Files.exists(redirectedDirectory.resolve("config.json")));
    }

    @Test
    void nonJavaProjectAsksBeforeUsingLimitedMode() throws Exception {
        RecordingConsole console = new RecordingConsole();

        initialize(new RecordingRepositoryAccess(true), console, Map.of(), successfulAi(), false);

        assertTrue(console.output().contains("limited mode"));
        assertTrue(console.output().contains("Java code analysis during commits"));
        assertEquals(List.of(
                "Continue with BetterGit limited mode?",
                "Enable AI commit report generator?"), console.prompts());
        assertFalse(configuration().get("javaDetected").getAsBoolean());
    }

    @Test
    void cancellingLimitedModeDoesNotWriteProjectFiles() throws Exception {
        RecordingConsole console = new RecordingConsole("n");
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(false);

        initialize(repository, console, Map.of(), successfulAi(), false);

        assertFalse(Files.exists(projectPath.resolve(".bettergit")));
        assertEquals(0, repository.initializationCount);
        assertTrue(console.output().contains("initialization cancelled"));
    }

    @Test
    void persistsJavaFeatureSettingsAndProjectFacts() throws Exception {
        createJavaProject();

        initialize(
                new RecordingRepositoryAccess(true),
                new RecordingConsole("y", "y", "yes"),
                Map.of(),
                successfulAi(),
                false);

        JsonObject persisted = configuration();
        JsonObject settings = persisted.getAsJsonObject("settings");
        assertTrue(settings.get("classDiagramOnCommit").getAsBoolean());
        assertTrue(settings.get("testDurationTracking").getAsBoolean());
        assertTrue(settings.get("sonarQubeDocumentation").getAsBoolean());
        assertTrue(Files.isDirectory(projectPath.resolve(".bettergit/diagrams")));
        assertTrue(Files.readString(projectPath.resolve(".gitignore")).contains(".bettergit/diagrams/"));
        assertTrue(Files.isDirectory(projectPath.resolve(".bettergit/test-durations")));
        assertTrue(Files.readString(projectPath.resolve(".gitignore"))
                .contains(".bettergit/test-durations/"));
        assertEquals("2026-08-13T12:00:00Z", persisted.get("createdAt").getAsString());
        assertEquals(projectPath.toAbsolutePath().normalize().toString(), persisted.get("projectPath").getAsString());
        assertTrue(persisted.get("javaDetected").getAsBoolean());
    }

    @Test
    void acceptDefaultsDisablesOptionalFeaturesWithoutQuestions() throws Exception {
        createJavaProject();
        RecordingConsole console = new RecordingConsole("yes", "yes", "yes");

        initialize(new RecordingRepositoryAccess(true), console, Map.of(), successfulAi(), true);

        JsonObject settings = configuration().getAsJsonObject("settings");
        assertTrue(console.prompts().isEmpty());
        assertFalse(settings.get("classDiagramOnCommit").getAsBoolean());
        assertFalse(settings.get("testDurationTracking").getAsBoolean());
        assertFalse(settings.get("sonarQubeDocumentation").getAsBoolean());
        assertFalse(configuration().getAsJsonObject("ai").get("commitReportEnabled").getAsBoolean());
    }

    @Test
    void configuresAiCommitReportsWithoutPersistingASecret() throws Exception {
        RecordingConsole console = new RecordingConsole(
                "y", "y", "y", "2", "secret-api-key", "n", "selected-model", "yes");

        initialize(new RecordingRepositoryAccess(true), console, Map.of(), successfulAi(), false);

        JsonObject ai = configuration().getAsJsonObject("ai");
        assertTrue(ai.get("commitReportEnabled").getAsBoolean());
        assertTrue(ai.get("memoryEnabled").getAsBoolean());
        assertEquals("selected-model", ai.get("model").getAsString());
        assertFalse(Files.readString(projectPath.resolve(".bettergit/config.json")).contains("AI_API_KEY"));
        assertTrue(Files.readString(projectPath.resolve(".env")).contains("AI_API_KEY=secret-api-key"));
        assertFalse(console.output().contains("secret-api-key"));
        assertTrue(console.output().contains("API key was masked"));
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/context/project-map.json")));
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/context/recent-history.md")));
        assertTrue(Files.isDirectory(projectPath.resolve(".bettergit/reports")));
        assertTrue(Files.readString(projectPath.resolve(".gitignore")).contains(".bettergit/reports/"));
    }

    @Test
    void existingAiConfigurationOffersReconfigurationAndPromptsForAReplacementKey() throws Exception {
        createJavaProject();
        Files.writeString(projectPath.resolve(".env"), """
                AI_API_KEY=old-key
                AI_API_MODEL=old-model
                AI_API_URL=https://ai.example/generate
                """);
        RecordingConsole console = new RecordingConsole(
                "", "", "", "y", "y", "", "replacement-key", "", "", "y");

        initialize(new RecordingRepositoryAccess(true), console, Map.of(), successfulAi(), false);

        assertTrue(console.prompts().contains("AI is configured for old-model. Reconfigure it?"));
        assertTrue(console.prompts().stream().anyMatch(prompt -> prompt.startsWith("AI API key")));
        String env = Files.readString(projectPath.resolve(".env"));
        assertTrue(env.contains("AI_API_KEY=replacement-key"));
        assertFalse(env.contains("AI_API_KEY=old-key"));
    }

    @Test
    void existingAiModelSkipsTheModelQuestion() throws Exception {
        RecordingConsole console = new RecordingConsole("y", "y", "n");
        Map<String, String> environment = Map.of("AI_API_MODEL", "configured-model");

        initialize(new RecordingRepositoryAccess(true), console, environment, successfulAi(), false);

        JsonObject ai = configuration().getAsJsonObject("ai");
        assertEquals("configured-model", ai.get("model").getAsString());
        assertFalse(console.prompts().stream().anyMatch(prompt -> prompt.startsWith("AI model")));
    }

    @Test
    void writesGeneratedAiDocumentation() throws Exception {
        Files.writeString(projectPath.resolve("README.md"), "# Sample\nRun with Maven.");
        AiTextGenerator generator = (configuration, prompt) -> {
            assertTrue(prompt.contains("Run with Maven"));
            assertTrue(prompt.contains("invent facts"));
            return "# Generated overview\n";
        };

        initialize(
                new RecordingRepositoryAccess(true),
                new RecordingConsole(),
                COMPLETE_AI_ENVIRONMENT,
                generator,
                false);

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

        initialize(repository, new RecordingConsole(), COMPLETE_AI_ENVIRONMENT, failingGenerator, false);

        String documentation = Files.readString(projectPath.resolve(".bettergit/general.md"));
        assertEquals(1, repository.initializationCount);
        assertTrue(documentation.contains("AI request failed"));
        assertFalse(documentation.contains("provider unavailable"));
    }

    @Test
    void blankAiResponseCreatesPlaceholderAndInitializationSucceeds() throws Exception {
        Files.writeString(projectPath.resolve("README.md"), "# Sample");
        RecordingRepositoryAccess repository = new RecordingRepositoryAccess(false);

        initialize(repository, new RecordingConsole(), COMPLETE_AI_ENVIRONMENT,
                (configuration, prompt) -> "   ", false);

        String documentation = Files.readString(projectPath.resolve(".bettergit/general.md"));
        assertEquals(1, repository.initializationCount);
        assertTrue(documentation.contains("AI request failed"));
    }

    @Test
    void apiKeyIsNeverPersistedOrPrinted() throws Exception {
        Files.writeString(projectPath.resolve("README.md"), "# Sample");
        AiTextGenerator leakingGenerator = (configuration, prompt) -> "Accidental " + configuration.apiKey();
        RecordingConsole console = new RecordingConsole();

        initialize(
                new RecordingRepositoryAccess(true),
                console,
                COMPLETE_AI_ENVIRONMENT,
                leakingGenerator,
                false);

        String persistedConfiguration = Files.readString(projectPath.resolve(".bettergit/config.json"));
        String documentation = Files.readString(projectPath.resolve(".bettergit/general.md"));
        assertFalse(persistedConfiguration.contains("secret-api-key"));
        assertFalse(documentation.contains("secret-api-key"));
        assertFalse(console.output().contains("secret-api-key"));
        assertFalse(console.errors().contains("secret-api-key"));
        assertTrue(documentation.contains("[REDACTED]"));
    }

    @Test
    void interactiveFeatureSelectionsArePersisted() throws Exception {
        createJavaProject();
        RecordingConsole console = new RecordingConsole("y", "n", "YES");

        initialize(new RecordingRepositoryAccess(true), console, Map.of(), successfulAi(), false);

        JsonObject settings = configuration().getAsJsonObject("settings");
        assertEquals(List.of(
                "Select the Java features to enable",
                "Enable AI commit report generator?"), console.prompts());
        assertTrue(settings.get("classDiagramOnCommit").getAsBoolean());
        assertFalse(settings.get("testDurationTracking").getAsBoolean());
        assertTrue(settings.get("sonarQubeDocumentation").getAsBoolean());
    }

    @Test
    void preservesGitIgnoreContentWhenAddingEnv() throws Exception {
        Files.writeString(projectPath.resolve(".env"), "AI_API_KEY=local-key\n");
        Files.writeString(projectPath.resolve(".gitignore"), "target/\n*.log\n");

        initialize(new RecordingRepositoryAccess(false), new RecordingConsole(), Map.of(), successfulAi(), false);

        String gitIgnore = Files.readString(projectPath.resolve(".gitignore"));
        assertTrue(gitIgnore.contains("target/\n*.log\n"));
        assertTrue(Arrays.asList(gitIgnore.split("\\R")).contains(".env"));
    }

    private void initialize(
            RecordingRepositoryAccess repository,
            RecordingConsole console,
            Map<String, String> environment,
            AiTextGenerator aiTextGenerator,
            boolean acceptDefaults) throws Exception {
        BetterGitFileStore fileStore = new BetterGitFileStore();
        BetterGitInitializer initializer = new BetterGitInitializer(new InitializationDependencies(
                repository,
                console,
                new AiConfigurationLoader(),
                new AiSetupService(fileStore, new AiCredentialStore()),
                new BetterGitConfigurationLoader(),
                new JavaProjectDetector(),
                new MarkdownProjectScanner(),
                new ProjectDocumentationGenerator(aiTextGenerator),
                fileStore,
                new AiMemoryStore(new ProjectMapScanner()),
                environment,
                Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC)));
        initializer.initialize(new InitConfiguration(
                projectPath,
                acceptDefaults ? InitializationMode.SAFE_DEFAULTS : InitializationMode.INTERACTIVE));
    }

    private JsonObject configuration() throws Exception {
        return JsonParser.parseString(Files.readString(projectPath.resolve(".bettergit/config.json"))).getAsJsonObject();
    }

    private void createJavaProject() throws Exception {
        Files.writeString(projectPath.resolve("pom.xml"), "<project/>");
    }

    private static String readUnchecked(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static AiTextGenerator successfulAi() {
        return (configuration, prompt) -> "# Generated";
    }

    static final class RecordingRepositoryAccess implements RepositoryAccess {
        private final boolean repositoryExists;
        private int initializationCount;
        private Consumer<Path> beforeInitialization = ignored -> { };

        RecordingRepositoryAccess(boolean repositoryExists) {
            this.repositoryExists = repositoryExists;
        }

        @Override
        public boolean hasRepository(Path ignored) {
            return repositoryExists;
        }

        @Override
        public void initialize(Path projectPath) {
            beforeInitialization.accept(projectPath);
            initializationCount++;
        }
    }
}
