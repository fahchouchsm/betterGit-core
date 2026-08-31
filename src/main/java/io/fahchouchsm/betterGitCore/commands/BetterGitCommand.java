package io.fahchouchsm.betterGitCore.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

/** Root command for the BetterGit command hierarchy. */
@Command(
        name = "bettergit",
        description = "BetterGit - Git workflows enhanced for Java projects.",
        mixinStandardHelpOptions = true,
        versionProvider = BetterGitVersionProvider.class,
        sortOptions = false,
        usageHelpAutoWidth = true,
        footer = "All other Git commands are available directly, for example: bettergit add .",
        synopsisHeading = "%nUsage:%n  ",
        optionListHeading = "%nOptions:%n",
        commandListHeading = "%nCommands:%n")
public final class BetterGitCommand implements Runnable {
    @Spec
    private CommandSpec commandSpec;

    @Option(
            names = {"-C", "--no-color"},
            description = "Disable coloured terminal output.",
            scope = ScopeType.INHERIT)
    private boolean noColor;

    @Option(
            names = {"-v", "--verbose"},
            description = "Show technical diagnostic information.",
            scope = ScopeType.INHERIT)
    private boolean verbose;

    @Override
    public void run() {
        commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
    }

    boolean noColor() {
        return noColor;
    }

    boolean verbose() {
        return verbose;
    }
}
