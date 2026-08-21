package io.fahchouchsm.betterGitCore.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps Git's full command surface available without duplicating its command grammar. */
final class NativeGitCommand {
    private final Path workingDirectory;

    NativeGitCommand(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    int execute(String[] arguments) throws IOException, InterruptedException {
        Process gitProcess = new ProcessBuilder(command(arguments))
                .directory(workingDirectory.toFile())
                .inheritIO()
                .start();
        return gitProcess.waitFor();
    }

    private static List<String> command(String[] arguments) {
        List<String> command = new ArrayList<>(arguments.length + 1);
        command.add("git");
        Collections.addAll(command, arguments);
        return command;
    }
}
