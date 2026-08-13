package io.fahchouchsm.betterGitCore.commands.init;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
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
import java.util.Locale;

/** Coordinates BetterGit setup without implementing Git or AI provider logic. */
public final class BetterGitInitializer {
    private final InitializationDependencies dependencies;

    public BetterGitInitializer(InitializationDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public void initialize(InitConfiguration request) throws IOException {
        Path projectPath = request.projectPath().toAbsolutePath().normalize();
        ConsolePort console = dependencies.console();
        GitPresence gitPresence = dependencies.repositoryAccess().isInsideRepository(projectPath)
                ? GitPresence.EXISTING
                : GitPresence.MISSING;
        reportGitDetection(console, gitPresence);
        SetupPlan setup = collectSetup(projectPath, request.mode(), gitPresence);
        persistSetup(projectPath, setup);
        initializeGitLast(projectPath, gitPresence, console);
        printSummary(console, setup);
    }

    private SetupPlan collectSetup(
            Path projectPath, InitializationMode mode, GitPresence gitPresence) throws IOException {
        ConsolePort console = dependencies.console();
        AiConfiguration aiConfiguration = dependencies.aiConfigurationLoader()
                .load(projectPath, dependencies.environment());
        reportAiAvailability(console, aiConfiguration);
        ProjectMode projectMode = dependencies.javaProjectDetector().isJavaProject(projectPath)
                ? ProjectMode.JAVA
                : ProjectMode.LIMITED;
        reportJavaSupport(console, projectMode);
        FeatureSettings settings = projectMode == ProjectMode.JAVA
                ? collectJavaSettings(console, mode)
                : FeatureSettings.disabled();
        DocumentationResult documentation = generateDocumentation(projectPath, aiConfiguration);
        Instant createdAt = dependencies.clock().instant();
        ProjectFacts facts = new ProjectFacts(
                projectMode, gitPresence, settings, aiConfiguration.isComplete());
        BetterGitConfiguration configuration = createConfiguration(projectPath, facts, createdAt);
        return new SetupPlan(configuration, documentation, projectMode, gitPresence);
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
        if (!setup.configuration().gitAlreadyExisted() && Files.isRegularFile(projectPath.resolve(".env"))) {
            dependencies.fileStore().ensureEnvIgnored(projectPath);
            console.diagnostic("Ensured the project .env file is ignored by Git.");
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
        return new FeatureSettings(
                askYesNo(console, "Save a class diagram on each commit? [y/N]: "),
                askYesNo(console, "Track test duration on each commit? [y/N]: "),
                askYesNo(console, "Generate SonarQube documentation? [y/N]: "));
    }

    private static boolean askYesNo(ConsolePort console, String prompt) throws IOException {
        while (true) {
            String answer = console.readLine(prompt).trim().toLowerCase(Locale.ROOT);
            if (answer.isEmpty() || "n".equals(answer) || "no".equals(answer)) {
                return false;
            }
            if ("y".equals(answer) || "yes".equals(answer)) {
                return true;
            }
            console.warning("Please answer y, yes, n, or no. Press Enter for no.");
        }
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
                facts.aiDocumentationAvailable());
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
                General documentation: %s

                Next step:
                  bettergit --help""".formatted(
                setup.projectMode().summaryLabel(),
                setup.gitPresence().summaryLabel(),
                enabled(settings.classDiagramOnCommit()),
                enabled(settings.testDurationTracking()),
                enabled(settings.sonarQubeDocumentation()),
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
            boolean aiDocumentationAvailable) {
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
