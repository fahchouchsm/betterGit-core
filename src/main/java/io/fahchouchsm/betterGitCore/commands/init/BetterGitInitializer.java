package io.fahchouchsm.betterGitCore.commands.init;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commands.console.ConfirmationDefault;
import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.documentation.DocumentationResult;
import io.fahchouchsm.betterGitCore.documentation.DocumentationStatus;
import io.fahchouchsm.betterGitCore.project.MarkdownScanResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Coordinates BetterGit setup without implementing Git or AI provider logic. */
public final class BetterGitInitializer {
    private final InitializationDependencies dependencies;

    public BetterGitInitializer(InitializationDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public void initialize(InitConfiguration request) throws IOException {
        Path projectPath = request.projectPath().toAbsolutePath().normalize();
        ConsolePort console = dependencies.console();
        GitPresence gitPresence = gitPresence(projectPath);
        reportGitDetection(console, gitPresence);
        ProjectMode projectMode = projectMode(projectPath);
        reportJavaSupport(console, projectMode);
        if (!continueInitialization(console, request.mode(), projectMode)) {
            console.warning("BetterGit initialization cancelled. No files were changed.");
            return;
        }
        SetupPlan setup = collectSetup(projectPath, request.mode(), gitPresence, projectMode);
        persistSetup(projectPath, setup);
        initializeGitLast(projectPath, gitPresence, console);
        printSummary(console, setup);
    }

    private GitPresence gitPresence(Path projectPath) {
        return dependencies.repositoryAccess().isInsideRepository(projectPath)
                ? GitPresence.EXISTING
                : GitPresence.MISSING;
    }

    private ProjectMode projectMode(Path projectPath) {
        return dependencies.javaProjectDetector().isJavaProject(projectPath)
                ? ProjectMode.JAVA
                : ProjectMode.LIMITED;
    }

    private SetupPlan collectSetup(
            Path projectPath,
            InitializationMode mode,
            GitPresence gitPresence,
            ProjectMode projectMode) throws IOException {
        ConsolePort console = dependencies.console();
        AiConfiguration loadedAiConfiguration = loadAiConfiguration(projectPath);
        FeatureSettings settings = projectMode == ProjectMode.JAVA
                ? collectJavaSettings(console, mode)
                : FeatureSettings.disabled();
        AiSetup aiSetup = collectAiSetup(console, mode, loadedAiConfiguration);
        AiConfiguration aiConfiguration = aiSetup.configuration();
        reportAiAvailability(console, aiConfiguration);
        DocumentationResult documentation = generateDocumentation(projectPath, aiConfiguration);
        Instant createdAt = dependencies.clock().instant();
        ProjectFacts facts = new ProjectFacts(projectMode, gitPresence, settings,
                aiConfiguration.isComplete(), aiSetup.commitSettings());
        BetterGitConfiguration configuration = createConfiguration(projectPath, facts, createdAt);
        return new SetupPlan(configuration, documentation, projectMode, gitPresence);
    }

    private AiConfiguration loadAiConfiguration(Path projectPath) throws IOException {
        Optional<BetterGitConfiguration> existing = dependencies.configurationLoader().load(projectPath);
        String configuredModel = existing.map(BetterGitConfiguration::ai)
                .map(AiCommitSettings::model)
                .orElse(null);
        return dependencies.aiConfigurationLoader()
                .load(projectPath, dependencies.environment(), configuredModel);
    }

    private DocumentationResult generateDocumentation(Path projectPath, AiConfiguration aiConfiguration)
            throws IOException {
        ConsolePort console = dependencies.console();
        MarkdownScanResult markdown = dependencies.markdownProjectScanner().scanProject(projectPath);
        reportMarkdownScan(console, markdown);
        DocumentationResult documentation = dependencies.documentationGenerator()
                .generate(aiConfiguration, markdown.content());
        reportDocumentation(console, documentation.status());
        return documentation;
    }

    private void persistSetup(Path projectPath, SetupPlan setup) throws IOException {
        ConsolePort console = dependencies.console();
        dependencies.fileStore().writeInitialization(
                projectPath, setup.configuration(), setup.documentation().content());
        console.diagnostic("Wrote .bettergit/config.json and .bettergit/general.md.");
        prepareCommitReports(projectPath, setup.configuration().ai());
        if (Files.isRegularFile(projectPath.resolve(".env"))) {
            dependencies.fileStore().ensureEnvIgnored(projectPath);
            console.diagnostic("Ensured the project .env file is ignored by Git.");
        }
    }

    private void prepareCommitReports(Path projectPath, AiCommitSettings settings) throws IOException {
        if (!settings.commitReportEnabled()) {
            return;
        }
        dependencies.fileStore().prepareCommitReports(projectPath);
        dependencies.fileStore().ensureReportsIgnored(projectPath);
        if (settings.memoryEnabled()) {
            dependencies.memoryStore().initialize(projectPath);
        }
    }

    private void initializeGitLast(Path projectPath, GitPresence gitPresence, ConsolePort console) {
        if (gitPresence == GitPresence.EXISTING) {
            console.info("Git initialization skipped because this project is already a Git repository.");
            return;
        }
        dependencies.repositoryAccess().initialize(projectPath);
        console.success("Git repository initialized successfully.");
    }

    private static void reportGitDetection(ConsolePort console, GitPresence gitPresence) {
        if (gitPresence == GitPresence.EXISTING) {
            console.info("Git repository detected. Existing Git setup will not be changed.");
        } else {
            console.info("No Git repository detected. Git will be initialized after BetterGit setup completes.");
        }
    }

    private static void reportAiAvailability(ConsolePort console, AiConfiguration configuration) {
        if (configuration.isComplete()) {
            console.success("AI documentation: available");
        } else {
            console.warning("AI documentation: unavailable. You can continue and configure it later.");
        }
    }

    private static void reportJavaSupport(ConsolePort console, ProjectMode projectMode) {
        if (projectMode == ProjectMode.JAVA) {
            console.success("Java project detected. Java-specific BetterGit features are available.");
            return;
        }
        console.warning("""
                Non-Java project detected. BetterGit will use limited mode.
                Unavailable features:
                - Class-diagram generation
                - Test-duration tracking
                - Java code analysis during commits""");
    }

    private static FeatureSettings collectJavaSettings(ConsolePort console, InitializationMode mode)
            throws IOException {
        if (mode == InitializationMode.SAFE_DEFAULTS) {
            console.diagnostic("Using safe defaults for all optional Java features.");
            return FeatureSettings.disabled();
        }
        List<Boolean> selections = console.chooseMany(
                "Select the Java features to enable",
                List.of(
                        "Save a class diagram on each commit",
                        "Track test duration on each commit",
                        "Generate SonarQube documentation"));
        return new FeatureSettings(selections.get(0), selections.get(1), selections.get(2));
    }

    private static AiSetup collectAiSetup(
            ConsolePort console, InitializationMode mode, AiConfiguration configuration) throws IOException {
        if (mode == InitializationMode.SAFE_DEFAULTS) {
            return new AiSetup(configuration, AiCommitSettings.disabled(configuration.model()));
        }
        boolean enabled = console.confirm(
                "Enable AI commit report generator?", ConfirmationDefault.NO);
        if (!enabled) {
            return new AiSetup(configuration, AiCommitSettings.disabled(configuration.model()));
        }
        if (!configuration.hasApiKey()) {
            console.warning("AI_API_KEY is not configured; commit reports will be skipped until it is supplied.");
        }
        String model = configuredModel(console, configuration.model());
        boolean memoryEnabled = console.confirm(
                "Maintain local BetterGit AI memory/context?", ConfirmationDefault.YES);
        return new AiSetup(
                new AiConfiguration(configuration.apiKey(), model, configuration.apiUrl()),
                new AiCommitSettings(true, memoryEnabled, model));
    }

    private static String configuredModel(ConsolePort console, String currentModel) throws IOException {
        if (currentModel != null && !currentModel.isBlank()) {
            return currentModel;
        }
        String selectedModel = console.readLine(
                "AI model (leave blank to configure AI_API_MODEL later): ").trim();
        return selectedModel.isBlank() ? null : selectedModel;
    }

    private static boolean continueInitialization(
            ConsolePort console, InitializationMode mode, ProjectMode projectMode) throws IOException {
        if (mode == InitializationMode.SAFE_DEFAULTS || projectMode == ProjectMode.JAVA) {
            return true;
        }
        return console.confirm("Continue with BetterGit limited mode?", ConfirmationDefault.YES);
    }

    private static void reportMarkdownScan(ConsolePort console, MarkdownScanResult scan) {
        console.diagnostic("Scanned " + scan.includedFiles() + " Markdown file(s).");
        if (scan.contentWasLimited()) {
            console.warning("Some Markdown content was skipped to keep AI input within safe limits ("
                    + scan.skippedFiles() + " file(s) skipped, "
                    + scan.truncatedFiles() + " file(s) truncated).");
        }
    }

    private static void reportDocumentation(ConsolePort console, DocumentationStatus status) {
        switch (status) {
            case GENERATED -> console.success("General documentation generated at .bettergit/general.md.");
            case AI_REQUEST_FAILED ->
                    console.warning("AI documentation generation failed; a safe placeholder will be created.");
            case NO_MARKDOWN_FOUND ->
                    console.warning("No project Markdown was found; a documentation placeholder will be created.");
            case AI_NOT_CONFIGURED ->
                    console.info("AI is not configured; a documentation placeholder will be created.");
        }
    }

    private static BetterGitConfiguration createConfiguration(
            Path projectPath,
            ProjectFacts facts,
            Instant createdAt) {
        return new BetterGitConfiguration(
                BetterGitConfiguration.CURRENT_SCHEMA_VERSION,
                createdAt.toString(),
                projectPath.toString(),
                facts.projectMode() == ProjectMode.JAVA,
                facts.gitPresence() == GitPresence.EXISTING,
                facts.settings(),
                facts.aiDocumentationAvailable(),
                facts.aiCommitSettings());
    }

    private static void printSummary(ConsolePort console, SetupPlan setup) {
        console.success("BetterGit initialization complete.");
        console.info(summaryText(setup));
    }

    private static String summaryText(SetupPlan setup) {
        FeatureSettings settings = setup.configuration().settings();
        return """

                Project type: %s
                Git: %s
                Class diagrams: %s
                Test duration tracking: %s
                SonarQube documentation: %s
                AI commit reports: %s
                AI memory/context: %s
                General documentation: %s

                Next step:
                  bettergit --help""".formatted(
                setup.projectMode().summaryLabel(),
                setup.gitPresence().summaryLabel(),
                enabled(settings.classDiagramOnCommit()),
                enabled(settings.testDurationTracking()),
                enabled(settings.sonarQubeDocumentation()),
                enabled(setup.configuration().ai().commitReportEnabled()),
                enabled(setup.configuration().ai().memoryEnabled()),
                setup.documentation().status() == DocumentationStatus.GENERATED
                        ? "generated"
                        : "placeholder created");
    }

    private static String enabled(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private record SetupPlan(
            BetterGitConfiguration configuration,
            DocumentationResult documentation,
            ProjectMode projectMode,
            GitPresence gitPresence) {
    }

    private record ProjectFacts(
            ProjectMode projectMode,
            GitPresence gitPresence,
            FeatureSettings settings,
            boolean aiDocumentationAvailable,
            AiCommitSettings aiCommitSettings) {
    }

    private record AiSetup(AiConfiguration configuration, AiCommitSettings commitSettings) {
    }

    private enum ProjectMode {
        JAVA("Java"),
        LIMITED("Limited mode");

        private final String summaryLabel;

        ProjectMode(String summaryLabel) {
            this.summaryLabel = summaryLabel;
        }

        private String summaryLabel() {
            return summaryLabel;
        }
    }

    private enum GitPresence {
        EXISTING("already present"),
        MISSING("initialized");

        private final String summaryLabel;

        GitPresence(String summaryLabel) {
            this.summaryLabel = summaryLabel;
        }

        private String summaryLabel() {
            return summaryLabel;
        }
    }
}
