package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.configuration.FeatureStoragePreparer;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeSettings;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "features",
        description = "Enable or disable BetterGit project features.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class FeaturesCommand implements Callable<Integer> {
    private final FeaturesCommandDependencies dependencies;
    private final Path invocationDirectory;

    @Parameters(index = "0", arity = "0..1", paramLabel = "DIRECTORY",
            description = "Project directory to configure (default: current directory).")
    private Path requestedDirectory;

    FeaturesCommand(FeaturesCommandDependencies dependencies, Path invocationDirectory) {
        this.dependencies = dependencies;
        this.invocationDirectory = invocationDirectory;
    }

    @Override
    public Integer call() throws IOException {
        Path projectPath = ProjectDirectoryResolver.resolve(invocationDirectory, requestedDirectory);
        if (!Files.isDirectory(projectPath)) {
            dependencies.console().failure("Project directory does not exist or is not a directory: " + projectPath);
            return CommandLine.ExitCode.USAGE;
        }
        return updateFeatures(projectPath);
    }

    private int updateFeatures(Path projectPath) throws IOException {
        Optional<BetterGitConfiguration> stored = dependencies.configurationLoader().load(projectPath);
        if (stored.isEmpty()) {
            dependencies.console().failure(
                    "No BetterGit configuration found. Run 'bettergit init " + projectPath + "' first.");
            return CommandLine.ExitCode.USAGE;
        }
        BetterGitConfiguration configuration = stored.orElseThrow();
        if (!configuration.javaDetected()) {
            dependencies.console().failure("Java-specific features are unavailable for this project.");
            return CommandLine.ExitCode.USAGE;
        }
        saveFeatures(projectPath, configuration);
        return CommandLine.ExitCode.OK;
    }

    private void saveFeatures(Path projectPath, BetterGitConfiguration configuration) throws IOException {
        FeatureSettings selected = FeatureMenu.select(
                dependencies.console(), "Select the Java features to enable", configuration.settings());
        selected = configureSonarQube(projectPath, selected);
        dependencies.fileStore().writeConfiguration(projectPath, configuration.withSettings(selected));
        new FeatureStoragePreparer(dependencies.fileStore()).prepare(projectPath, selected);
        dependencies.console().success("BetterGit feature settings updated.");
        dependencies.console().info(featureSummary(selected));
    }

    private FeatureSettings configureSonarQube(Path projectPath, FeatureSettings selected) throws IOException {
        if (!selected.sonarQubeDocumentation()) {
            return selected;
        }
        SonarQubeSettingsCollector collector = new SonarQubeSettingsCollector(new SonarQubeSettingsDependencies(
                dependencies.sonarQubeConfigurationLoader(), dependencies.sonarQubeCredentialStore(),
                dependencies.fileStore(), dependencies.console(), dependencies.environment()));
        SonarQubeSettings configured = collector.configure(projectPath, selected.sonarQube());
        return selected.withSonarQube(configured);
    }

    private static String featureSummary(FeatureSettings settings) {
        return """
                Class diagrams: %s
                Test duration tracking: %s
                SonarQube quality gate: %s
                SonarQube triggers: %s
                SonarQube branches: %s""".formatted(
                enabled(settings.classDiagramOnCommit()),
                enabled(settings.testDurationTracking()),
                enabled(settings.sonarQubeDocumentation()),
                settings.sonarQube().trigger().name().toLowerCase().replace('_', ' '),
                settings.sonarQube().branches().isEmpty()
                        ? "all"
                        : String.join(", ", settings.sonarQube().branches()));
    }

    private static String enabled(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
