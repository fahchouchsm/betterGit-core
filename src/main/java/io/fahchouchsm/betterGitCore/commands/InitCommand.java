package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commands.init.BetterGitInitializer;
import io.fahchouchsm.betterGitCore.commands.init.InitConfiguration;
import io.fahchouchsm.betterGitCore.commands.init.InitializationMode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Picocli adapter for {@code bettergit init}. */
@Command(
        name = "init",
        description = "Configure BetterGit for the current project.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class InitCommand implements Callable<Integer> {
    public static final int SUCCESS = 0;

    private final BetterGitInitializer initializer;
    private final ConsolePort console;
    private final Path projectPath;

    @Option(
            names = "--yes",
            description = "Accept safe defaults for all optional settings.")
    private boolean acceptDefaults;

    public InitCommand(BetterGitInitializer initializer, ConsolePort console, Path projectPath) {
        this.initializer = initializer;
        this.console = console;
        this.projectPath = projectPath.toAbsolutePath().normalize();
    }

    @Override
    public Integer call() throws Exception {
        console.info("Initializing BetterGit in: " + projectPath);
        InitializationMode mode = acceptDefaults
                ? InitializationMode.SAFE_DEFAULTS
                : InitializationMode.INTERACTIVE;
        initializer.initialize(new InitConfiguration(projectPath, mode));
        return SUCCESS;
    }
}
