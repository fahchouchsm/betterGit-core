package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.history.GitHistoryReader;
import io.fahchouchsm.betterGitCore.history.HistoryDateParser;
import io.fahchouchsm.betterGitCore.history.HistoryJsonRenderer;
import io.fahchouchsm.betterGitCore.history.HistoryQuery;
import io.fahchouchsm.betterGitCore.history.HistoryTextRenderer;
import io.fahchouchsm.betterGitCore.history.HistoryView;
import io.fahchouchsm.betterGitCore.history.RepositoryHistory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "log",
        description = "Explore repository history with a modern, information-rich timeline.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class LogCommand implements Callable<Integer> {
    private final LogCommandDependencies dependencies;
    private final Path invocationDirectory;

    @Parameters(index = "0", arity = "0..1", paramLabel = "DIRECTORY",
            description = "Repository directory to inspect (default: current directory).")
    private Path requestedDirectory;

    @Option(names = {"-n", "--limit"}, defaultValue = "20",
            description = "Maximum commits to show, from 1 to 1000 (default: ${DEFAULT-VALUE}).")
    private int limit;

    @Option(names = "--all", description = "Include commits reachable from every branch and tag.")
    private boolean allReferences;

    @Option(names = "--author", paramLabel = "TEXT", description = "Filter author names containing TEXT.")
    private String author;

    @Option(names = "--grep", paramLabel = "TEXT", description = "Filter commit messages containing TEXT.")
    private String message;

    @Option(names = "--since", paramLabel = "WHEN",
            description = "Only commits after WHEN: ISO date/time, YYYY-MM-DD, today, yesterday, or 7d.")
    private String since;

    @Option(names = "--until", paramLabel = "WHEN", description = "Only commits before WHEN.")
    private String until;

    @Option(names = "--path", paramLabel = "PATH", description = "Only commits touching PATH.")
    private String path;

    @Option(names = "--reverse", description = "Show the selected commits oldest first.")
    private boolean reverse;

    @Option(names = {"-d", "--details"}, description = "Show messages, identities, parents, and signatures.")
    private boolean details;

    @Option(names = "--files", description = "Show changed files for every commit.")
    private boolean files;

    @Option(names = "--full-hash", description = "Show full commit hashes.")
    private boolean fullHash;

    @Option(names = "--no-graph", description = "Hide timeline and merge markers.")
    private boolean noGraph;

    @Option(names = "--no-stats", description = "Hide per-commit change statistics.")
    private boolean noStatistics;

    @Option(names = "--json", description = "Emit complete history information as JSON.")
    private boolean json;

    LogCommand(LogCommandDependencies dependencies, Path invocationDirectory) {
        this.dependencies = dependencies;
        this.invocationDirectory = invocationDirectory;
    }

    @Override
    public Integer call() {
        Path projectPath = ProjectDirectoryResolver.resolve(invocationDirectory, requestedDirectory);
        if (!Files.isDirectory(projectPath)) {
            dependencies.console().failure("Repository directory does not exist or is not a directory: " + projectPath);
            return CommandLine.ExitCode.USAGE;
        }
        return renderHistory(projectPath);
    }

    private int renderHistory(Path projectPath) {
        HistoryQuery query;
        try {
            query = query();
        } catch (IllegalArgumentException exception) {
            dependencies.console().failure(exception.getMessage());
            return CommandLine.ExitCode.USAGE;
        }
        RepositoryHistory history = dependencies.reader().read(projectPath, query);
        String rendered = rendered(history);
        dependencies.console().out().print(rendered);
        dependencies.console().out().flush();
        return CommandLine.ExitCode.OK;
    }

    private String rendered(RepositoryHistory history) {
        return json
                ? dependencies.jsonRenderer().render(history)
                : dependencies.textRenderer().render(history,
                new HistoryView(details, files, fullHash, !noGraph, !noStatistics));
    }

    private HistoryQuery query() {
        HistoryDateParser dates = dependencies.dateParser();
        return new HistoryQuery(
                limit, allReferences, author, message, dates.parse(since), dates.parse(until), path, reverse,
                json || !noStatistics || files || details);
    }
}
