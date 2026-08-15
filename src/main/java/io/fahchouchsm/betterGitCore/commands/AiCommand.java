package io.fahchouchsm.betterGitCore.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "ai",
        description = "Configure and inspect BetterGit AI integration.",
        mixinStandardHelpOptions = true,
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class AiCommand implements Runnable {
    @Spec
    private CommandSpec commandSpec;

    @Override
    public void run() {
        commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
    }
}
