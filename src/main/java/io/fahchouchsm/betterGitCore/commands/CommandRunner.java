package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.JGitManager.JGitManager;
import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commands.console.ConsoleSettings;
import io.fahchouchsm.betterGitCore.commands.console.SystemConsoleAdapter;
import io.fahchouchsm.betterGitCore.commands.init.BetterGitInitializer;
import io.fahchouchsm.betterGitCore.commands.init.InitializationDependencies;
import io.fahchouchsm.betterGitCore.commitreport.AiCommitContextBuilder;
import io.fahchouchsm.betterGitCore.commitreport.AiCommitPromptBuilder;
import io.fahchouchsm.betterGitCore.commitreport.AiCommitReportGenerator;
import io.fahchouchsm.betterGitCore.commitreport.AiMemoryStore;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportDependencies;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportStore;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportValidator;
import io.fahchouchsm.betterGitCore.commitreport.JGitCommitDataSource;
import io.fahchouchsm.betterGitCore.commitreport.JavaSourceContextCollector;
import io.fahchouchsm.betterGitCore.commitreport.ProjectMapScanner;
import io.fahchouchsm.betterGitCore.commitreport.SensitiveContentFilter;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.documentation.AiSystemTextGenerator;
import io.fahchouchsm.betterGitCore.documentation.ProjectDocumentationGenerator;
import io.fahchouchsm.betterGitCore.project.JavaProjectDetector;
import io.fahchouchsm.betterGitCore.project.MarkdownProjectScanner;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Application entry point and Picocli composition root. */
public final class CommandRunner {
    private CommandRunner() {
    }

    public static void main(String[] arguments) {
        ConsolePort console = new SystemConsoleAdapter();
        JGitManager git = new JGitManager();
        CommandRuntime runtime = new CommandRuntime(
                Path.of("").toAbsolutePath().normalize(),
                console,
                new JGitRepositoryAccess(git),
                new JGitCommitDataSource(git),
                System.getenv(),
                Clock.systemUTC(),
                new AiSystemTextGenerator());
        int exitCode = execute(arguments, runtime);
        if (exitCode != CommandLine.ExitCode.OK) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] arguments, CommandRuntime runtime) {
        return createCommandLine(runtime).execute(arguments);
    }

    private static CommandLine createCommandLine(CommandRuntime runtime) {
        ConsolePort console = runtime.console();
        AiMemoryStore memoryStore = new AiMemoryStore(new ProjectMapScanner());
        BetterGitConfigurationLoader configurationLoader = new BetterGitConfigurationLoader();
        BetterGitInitializer initializer = initializer(runtime, memoryStore, configurationLoader);
        AiCommitReportGenerator reportGenerator = reportGenerator(runtime, memoryStore);
        CommitReportCommandDependencies reportDependencies = new CommitReportCommandDependencies(
                reportGenerator, configurationLoader, new AiConfigurationLoader(), new BetterGitFileStore(),
                console, runtime.environment());
        BetterGitCommand rootCommand = new BetterGitCommand();
        CommandLine commandLine = new CommandLine(rootCommand);
        commandLine.addSubcommand("init", new InitCommand(initializer, console, runtime.projectPath()));
        commandLine.addSubcommand("commit-report", new CommitReportCommand(reportDependencies, runtime.projectPath()));
        commandLine.addSubcommand("help", new CommandLine.HelpCommand());
        commandLine.setOut(console.out());
        commandLine.setErr(console.err());
        installHandlers(commandLine, console, rootCommand);
        return commandLine;
    }

    private static BetterGitInitializer initializer(
            CommandRuntime runtime,
            AiMemoryStore memoryStore,
            BetterGitConfigurationLoader configurationLoader) {
        return new BetterGitInitializer(new InitializationDependencies(
                runtime.repositoryAccess(),
                runtime.console(),
                new AiConfigurationLoader(),
                configurationLoader,
                new JavaProjectDetector(),
                new MarkdownProjectScanner(),
                new ProjectDocumentationGenerator(runtime.aiTextGenerator()),
                new BetterGitFileStore(),
                memoryStore,
                runtime.environment(),
                runtime.clock()));
    }

    private static AiCommitReportGenerator reportGenerator(CommandRuntime runtime, AiMemoryStore memoryStore) {
        SensitiveContentFilter filter = new SensitiveContentFilter();
        return new AiCommitReportGenerator(new CommitReportDependencies(
                runtime.commitDataSource(),
                runtime.aiTextGenerator(),
                memoryStore,
                new AiCommitContextBuilder(filter, new JavaSourceContextCollector()),
                new AiCommitPromptBuilder(),
                filter,
                new CommitReportValidator(),
                new CommitReportStore(),
                runtime.clock()));
    }

    private static void installHandlers(
            CommandLine commandLine, ConsolePort console, BetterGitCommand rootCommand) {
        commandLine.setExecutionStrategy(parseResult -> {
            configureOutput(commandLine, console, rootCommand);
            return new CommandLine.RunLast().execute(parseResult);
        });
        commandLine.setParameterExceptionHandler((exception, ignoredArguments) ->
                reportInvalidInput(exception, commandLine, console, rootCommand));
        commandLine.setExecutionExceptionHandler((exception, ignoredCommandLine, ignoredParseResult) ->
                reportExecutionFailure(exception, commandLine, console, rootCommand));
    }

    private static int reportInvalidInput(
            CommandLine.ParameterException exception,
            CommandLine commandLine,
            ConsolePort console,
            BetterGitCommand rootCommand) {
        configureOutput(commandLine, console, rootCommand);
        console.failure(exception.getMessage());
        console.err().println("Run 'bettergit --help' to see available commands and options.");
        return CommandLine.ExitCode.USAGE;
    }

    private static int reportExecutionFailure(
            Exception exception,
            CommandLine commandLine,
            ConsolePort console,
            BetterGitCommand rootCommand) {
        configureOutput(commandLine, console, rootCommand);
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        console.failure("BetterGit command failed.");
        if (console.isVerbose()) {
            printSecretSafeStackTrace(cause, console);
        } else {
            console.err().println("Run the command again with --verbose for technical diagnostics.");
        }
        return CommandLine.ExitCode.SOFTWARE;
    }

    private static void printSecretSafeStackTrace(Throwable failure, ConsolePort console) {
        Set<Throwable> printedFailures = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable currentFailure = failure;
        while (currentFailure != null && printedFailures.add(currentFailure)) {
            console.err().println(currentFailure.getClass().getName());
            for (StackTraceElement frame : currentFailure.getStackTrace()) {
                console.err().println("\tat " + frame);
            }
            currentFailure = currentFailure.getCause();
            if (currentFailure != null && !printedFailures.contains(currentFailure)) {
                console.err().println("Caused by:");
            }
        }
    }

    private static void configureOutput(
            CommandLine commandLine, ConsolePort console, BetterGitCommand rootCommand) {
        console.configure(new ConsoleSettings(rootCommand.noColor(), rootCommand.verbose()));
        setAnsiRecursively(commandLine, rootCommand.noColor() ? Ansi.OFF : Ansi.AUTO);
    }

    private static void setAnsiRecursively(CommandLine commandLine, Ansi ansi) {
        commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(ansi));
        commandLine.getSubcommands().values().forEach(subcommand -> setAnsiRecursively(subcommand, ansi));
    }
}
