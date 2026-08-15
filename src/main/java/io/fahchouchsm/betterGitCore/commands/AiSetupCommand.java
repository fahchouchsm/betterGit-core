package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "setup",
        description = "Securely configure the project AI key, model, and endpoint.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class AiSetupCommand implements Callable<Integer> {
    private final AiSetupCommandDependencies dependencies;
    private final Path invocationDirectory;

    @Parameters(index = "0", arity = "0..1", paramLabel = "DIRECTORY",
            description = "Project directory to configure (default: current directory).")
    private Path requestedDirectory;

    AiSetupCommand(AiSetupCommandDependencies dependencies, Path invocationDirectory) {
        this.dependencies = dependencies;
        this.invocationDirectory = invocationDirectory;
    }

    @Override
    public Integer call() throws Exception {
        Path projectPath = ProjectDirectoryResolver.resolve(invocationDirectory, requestedDirectory);
        if (!Files.isDirectory(projectPath)) {
            dependencies.console().failure("Project directory does not exist or is not a directory: " + projectPath);
            return CommandLine.ExitCode.USAGE;
        }
        configure(projectPath);
        return CommandLine.ExitCode.OK;
    }

    private void configure(Path projectPath) throws Exception {
        Optional<BetterGitConfiguration> stored = dependencies.configurationLoader().load(projectPath);
        String configuredModel = stored.map(BetterGitConfiguration::ai)
                .map(ai -> ai.model()).orElse(null);
        AiConfiguration current = dependencies.aiConfigurationLoader().load(
                projectPath, dependencies.environment(), configuredModel);
        AiConfiguration configured = dependencies.setupService().complete(projectPath, current, dependencies.console());
        if (configured.isComplete() && stored.isPresent()) {
            dependencies.fileStore().writeConfiguration(projectPath,
                    stored.orElseThrow().withAiModel(configured.model()));
        }
    }
}
