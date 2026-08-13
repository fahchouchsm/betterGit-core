package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitInitializationException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitRepositoryPathException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitStateReadException;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.documentation.DocumentationResult;
import io.fahchouchsm.betterGitCore.documentation.DocumentationStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/** Implements the interactive bettergit init workflow for the current project directory. */
public final class InitCommand {
    public static final int SUCCESS = 0;
    public static final int INITIALIZATION_FAILED = 1;

    private final InitCommandDependencies dependencies;

    public InitCommand(InitCommandDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public int execute(Path projectPath) {
        try {
            boolean gitAlreadyExisted = dependencies.repositoryAccess().isInsideRepository(projectPath);
            InitSetup setup = collectSetup(projectPath, gitAlreadyExisted);
            persistSetup(projectPath, setup);
            initializeGitLast(projectPath, gitAlreadyExisted);
            reportCompletion(gitAlreadyExisted);
            return SUCCESS;
        } catch (IOException
                 | GitInitializationException
                 | GitRepositoryPathException
                 | GitStateReadException exception) {
            dependencies.console().println("BetterGit initialization failed before Git setup completed: "
                    + exception.getMessage());
            return INITIALIZATION_FAILED;
        }
    }

    private InitSetup collectSetup(Path projectPath, boolean gitAlreadyExisted) throws IOException {
        AiConfiguration aiConfiguration = dependencies.aiConfigurationLoader()
                .load(projectPath, dependencies.environment());
        reportAiAvailability(aiConfiguration);
        boolean javaDetected = dependencies.javaProjectDetector().isJavaProject(projectPath);
        FeatureSettings featureSettings = javaDetected ? askJavaSettings() : limitedModeSettings();
        String markdownContext = dependencies.markdownProjectScanner().scan(projectPath);
        DocumentationResult documentation = dependencies.documentationGenerator()
                .generate(aiConfiguration, markdownContext);
        reportDocumentationStatus(documentation.status());
        Instant initializationTime = dependencies.clock().instant();
        ConfigurationFacts configurationFacts = new ConfigurationFacts(
                javaDetected, gitAlreadyExisted, featureSettings, aiConfiguration.isComplete());
        BetterGitConfiguration configuration = configuration(configurationFacts, initializationTime);
        return new InitSetup(configuration, documentation.content());
    }

    private void persistSetup(Path projectPath, InitSetup setup) throws IOException {
        dependencies.fileStore().writeInitialization(projectPath, setup.configuration(), setup.documentation());
        if (!setup.configuration().gitAlreadyExisted() && Files.isRegularFile(projectPath.resolve(".env"))) {
            dependencies.fileStore().ensureEnvIgnored(projectPath);
        }
    }

    private void initializeGitLast(Path projectPath, boolean gitAlreadyExisted) {
        if (!gitAlreadyExisted) {
            dependencies.repositoryAccess().initialize(projectPath);
        }
    }

    private void reportCompletion(boolean gitAlreadyExisted) {
        dependencies.console().println(gitAlreadyExisted
                ? "BetterGit initialized. An existing Git repository was preserved."
                : "BetterGit initialized. Git was initialized as the final setup step.");
    }

    private void reportAiAvailability(AiConfiguration aiConfiguration) {
        if (aiConfiguration.isComplete()) {
            dependencies.console().println("AI documentation generation is available.");
        } else if (aiConfiguration.hasApiKey()) {
            dependencies.console().println("An AI API key was found, but the model or API URL is missing or invalid.");
        } else {
            dependencies.console().println(
                    "AI-generated documentation cannot be generated now; initialization will continue normally.");
        }
    }

    private FeatureSettings limitedModeSettings() {
        dependencies.console().println("No Java project was detected. BetterGit will use limited mode.");
        dependencies.console().println("Class diagrams, test-duration tracking, and Java code analysis will not work.");
        return FeatureSettings.disabled();
    }

    private FeatureSettings askJavaSettings() throws IOException {
        return new FeatureSettings(
                askYesNo("Save a class diagram on each commit? [y/N] "),
                askYesNo("Track test duration on each commit? [y/N] "),
                askYesNo("Generate SonarQube documentation? [y/N] "));
    }

    private boolean askYesNo(String prompt) throws IOException {
        while (true) {
            String answer = dependencies.console().readLine(prompt).trim().toLowerCase(Locale.ROOT);
            if (answer.isEmpty() || "n".equals(answer) || "no".equals(answer)) {
                return false;
            }
            if ("y".equals(answer) || "yes".equals(answer)) {
                return true;
            }
            dependencies.console().println("Please answer y, yes, n, or no. Press Enter for no.");
        }
    }

    private void reportDocumentationStatus(DocumentationStatus status) {
        if (status == DocumentationStatus.GENERATED) {
            dependencies.console().println("Generated .bettergit/general.md from project Markdown files.");
        } else if (status == DocumentationStatus.AI_REQUEST_FAILED) {
            dependencies.console().println("AI documentation generation failed; a safe placeholder will be used.");
        }
    }

    private static BetterGitConfiguration configuration(ConfigurationFacts facts, Instant initializationTime) {
        return new BetterGitConfiguration(
                BetterGitConfiguration.CURRENT_SCHEMA_VERSION,
                facts.javaDetected() ? "JAVA" : "LIMITED",
                facts.javaDetected(),
                facts.gitAlreadyExisted(),
                facts.settings(),
                facts.aiDocumentationAvailable(),
                initializationTime.toString(),
                initializationTime.toString());
    }

    private record InitSetup(BetterGitConfiguration configuration, String documentation) {
    }

    private record ConfigurationFacts(
            boolean javaDetected,
            boolean gitAlreadyExisted,
            FeatureSettings settings,
            boolean aiDocumentationAvailable) {
    }
}
