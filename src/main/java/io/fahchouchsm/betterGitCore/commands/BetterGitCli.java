package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.JGitManager.JGitManager;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.documentation.AiSystemTextGenerator;
import io.fahchouchsm.betterGitCore.documentation.ProjectDocumentationGenerator;
import io.fahchouchsm.betterGitCore.project.JavaProjectDetector;
import io.fahchouchsm.betterGitCore.project.MarkdownProjectScanner;

import java.nio.file.Path;
import java.time.Clock;

/** BetterGit command-line entry point. */
public final class BetterGitCli {
    public static final int INVALID_COMMAND = 2;

    private BetterGitCli() {
    }

    public static void main(String[] arguments) {
        CommandConsole console = new StandardCommandConsole();
        int exitCode = run(arguments, Path.of("").toAbsolutePath().normalize(), console);
        if (exitCode != InitCommand.SUCCESS) {
            System.exit(exitCode);
        }
    }

    static int run(String[] arguments, Path projectPath, CommandConsole console) {
        if (arguments.length != 1 || !"init".equals(arguments[0])) {
            console.println("Usage: bettergit init");
            return INVALID_COMMAND;
        }
        InitCommand command = new InitCommand(dependencies(console));
        return command.execute(projectPath);
    }

    private static InitCommandDependencies dependencies(CommandConsole console) {
        return new InitCommandDependencies(
                new JGitRepositoryAccess(new JGitManager()),
                console,
                new AiConfigurationLoader(),
                new JavaProjectDetector(),
                new MarkdownProjectScanner(),
                new ProjectDocumentationGenerator(new AiSystemTextGenerator()),
                new BetterGitFileStore(),
                System.getenv(),
                Clock.systemUTC());
    }
}
