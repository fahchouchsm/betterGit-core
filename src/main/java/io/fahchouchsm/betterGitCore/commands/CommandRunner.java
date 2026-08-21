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
import io.fahchouchsm.betterGitCore.configuration.AiCredentialStore;
import io.fahchouchsm.betterGitCore.configuration.AiSetupService;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.documentation.AiSystemTextGenerator;
import io.fahchouchsm.betterGitCore.documentation.ProjectDocumentationGenerator;
import io.fahchouchsm.betterGitCore.project.JavaProjectDetector;
import io.fahchouchsm.betterGitCore.project.MarkdownProjectScanner;
import io.fahchouchsm.betterGitCore.history.GitHistoryReader;
import io.fahchouchsm.betterGitCore.history.HistoryDateParser;
import io.fahchouchsm.betterGitCore.history.HistoryJsonRenderer;
import io.fahchouchsm.betterGitCore.history.HistoryTextRenderer;
import io.fahchouchsm.betterGitCore.history.RelativeTimeFormatter;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Application entry point and Picocli composition root. */
public final class CommandRunner {
    private static final Set<String> ROOT_ACTIONS = Set.of(
            "-h", "--help", "-V", "--version");
    private static final Set<String> BETTERGIT_GLOBAL_OPTIONS = Set.of(
            "-v", "--verbose", "--no-color");

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
                git::commitStagedChanges,
                System.getenv(),
                Clock.systemUTC(),
                new AiSystemTextGenerator());
        int exitCode = execute(arguments, runtime);
        if (exitCode != CommandLine.ExitCode.OK) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] arguments, CommandRuntime runtime) {
        CommandLine commandLine = createCommandLine(runtime);
        if (!usesEnhancedCommand(arguments, commandLine)) {
            return executeNativeGit(arguments, runtime);
        }
        return commandLine.execute(arguments);
    }

    private static boolean usesEnhancedCommand(String[] arguments, CommandLine commandLine) {
        if (arguments.length == 0) {
            return true;
        }
        for (String argument : arguments) {
            if (!BETTERGIT_GLOBAL_OPTIONS.contains(argument)) {
                return ROOT_ACTIONS.contains(argument)
                        || commandLine.getSubcommands().containsKey(argument);
            }
        }
        return true;
    }

    private static int executeNativeGit(String[] arguments, CommandRuntime runtime) {
        try {
            return new NativeGitCommand(runtime.projectPath()).execute(arguments);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            runtime.console().failure("BetterGit was interrupted while Git was running.");
        } catch (IOException exception) {
            runtime.console().failure(
                    "BetterGit could not start Git. Ensure Git is installed and available on PATH.");
        }
        return CommandLine.ExitCode.SOFTWARE;
    }

    private static CommandLine createCommandLine(CommandRuntime runtime) {
        ApplicationServices services = ApplicationServices.create();
        BetterGitInitializer initializer = initializer(runtime, services);
        AiCommitReportGenerator reportGenerator = reportGenerator(runtime, services);
        CommitCommandDependencies commitDependencies = new CommitCommandDependencies(
                runtime.commitExecutor(), reportGenerator, services.reportStore(), services.memoryStore(),
                services.configurationLoader(), services.aiConfigurationLoader(), services.aiSetupService(),
                services.fileStore(), runtime.console(), runtime.environment());
        AiSetupCommandDependencies aiSetupDependencies = new AiSetupCommandDependencies(
                services.aiConfigurationLoader(), services.aiSetupService(), services.configurationLoader(),
                services.fileStore(), runtime.console(), runtime.environment());
        return commandLine(runtime, initializer, commitDependencies, aiSetupDependencies);
    }

    private static CommandLine commandLine(
            CommandRuntime runtime,
            BetterGitInitializer initializer,
            CommitCommandDependencies commitDependencies,
            AiSetupCommandDependencies aiSetupDependencies) {
        ConsolePort console = runtime.console();
        BetterGitCommand rootCommand = new BetterGitCommand();
        CommandLine commandLine = new CommandLine(rootCommand);
        commandLine.addSubcommand("init", new InitCommand(initializer, console, runtime.projectPath()));
        commandLine.addSubcommand("commit", new CommitCommand(commitDependencies, runtime.projectPath()));
        commandLine.addSubcommand("log", logCommand(runtime));
        commandLine.addSubcommand("ai", aiCommand(runtime, aiSetupDependencies));
        commandLine.addSubcommand("help", new CommandLine.HelpCommand());
        commandLine.setOut(console.out());
        commandLine.setErr(console.err());
        installHandlers(commandLine, console, rootCommand);
        return commandLine;
    }

    private static BetterGitInitializer initializer(CommandRuntime runtime, ApplicationServices services) {
        return new BetterGitInitializer(new InitializationDependencies(
                runtime.repositoryAccess(),
                runtime.console(),
                services.aiConfigurationLoader(),
                services.aiSetupService(),
                services.configurationLoader(),
                new JavaProjectDetector(),
                new MarkdownProjectScanner(),
                new ProjectDocumentationGenerator(runtime.aiTextGenerator()),
                services.fileStore(),
                services.memoryStore(),
                runtime.environment(),
                runtime.clock()));
    }

    private static AiCommitReportGenerator reportGenerator(CommandRuntime runtime, ApplicationServices services) {
        SensitiveContentFilter filter = new SensitiveContentFilter();
        return new AiCommitReportGenerator(new CommitReportDependencies(
                runtime.commitDataSource(),
                runtime.aiTextGenerator(),
                services.memoryStore(),
                new AiCommitContextBuilder(filter, new JavaSourceContextCollector()),
                new AiCommitPromptBuilder(),
                filter,
                new CommitReportValidator(),
                services.reportStore(),
                runtime.clock()));
    }

    private static LogCommand logCommand(CommandRuntime runtime) {
        return new LogCommand(new LogCommandDependencies(
                new GitHistoryReader(),
                new HistoryTextRenderer(runtime.clock(), new RelativeTimeFormatter()),
                new HistoryJsonRenderer(),
                new HistoryDateParser(runtime.clock()),
                runtime.console()), runtime.projectPath());
    }

    private static CommandLine aiCommand(
            CommandRuntime runtime, AiSetupCommandDependencies dependencies) {
        CommandLine aiCommand = new CommandLine(new AiCommand());
        aiCommand.addSubcommand("setup", new AiSetupCommand(dependencies, runtime.projectPath()));
        return aiCommand;
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

    private record ApplicationServices(
            AiMemoryStore memoryStore,
            BetterGitConfigurationLoader configurationLoader,
            BetterGitFileStore fileStore,
            AiConfigurationLoader aiConfigurationLoader,
            AiSetupService aiSetupService,
            CommitReportStore reportStore) {

        private static ApplicationServices create() {
            BetterGitFileStore fileStore = new BetterGitFileStore();
            return new ApplicationServices(
                    new AiMemoryStore(new ProjectMapScanner()),
                    new BetterGitConfigurationLoader(),
                    fileStore,
                    new AiConfigurationLoader(),
                    new AiSetupService(fileStore, new AiCredentialStore()),
                    new CommitReportStore());
        }
    }
}
