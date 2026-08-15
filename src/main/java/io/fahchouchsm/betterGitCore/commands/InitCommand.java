package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commands.init.BetterGitInitializer;
import io.fahchouchsm.betterGitCore.commands.init.InitConfiguration;
import io.fahchouchsm.betterGitCore.commands.init.InitializationMode;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Picocli adapter for {@code bettergit init}. */
@Command(
        name = "init",
        description = "Configure BetterGit for the current or specified project.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class InitCommand implements Callable<Integer> {
    public static final int SUCCESS = 0;

    private final BetterGitInitializer initializer;
    private final ConsolePort console;
    private final Path invocationDirectory;

    @Parameters(
            index = "0",
            arity = "0..1",
            paramLabel = "DIRECTORY",
            description = "Project directory to initialize (default: current directory).")
    private Path requestedDirectory;

    @Option(
            names = {"-y", "--yes"},
            description = "Accept safe defaults for all optional settings.")
    private boolean acceptDefaults;

    public InitCommand(BetterGitInitializer initializer, ConsolePort console, Path projectPath) {
        this.initializer = initializer;
        this.console = console;
        this.invocationDirectory = projectPath;
    }

    @Override
    public Integer call() throws Exception {
        Path targetDirectory = targetDirectory();
        if (!Files.isDirectory(targetDirectory)) {
            console.failure("Project directory does not exist or is not a directory: " + targetDirectory);
            return CommandLine.ExitCode.USAGE;
        }
        console.info("Initializing BetterGit in: " + targetDirectory);
        InitializationMode mode = acceptDefaults
                ? InitializationMode.SAFE_DEFAULTS
                : InitializationMode.INTERACTIVE;
        initializer.initialize(new InitConfiguration(targetDirectory, mode));
        return SUCCESS;
    }

    private Path targetDirectory() {
        return ProjectDirectoryResolver.resolve(invocationDirectory, requestedDirectory);
    }
}
