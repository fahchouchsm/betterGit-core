package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BetterGitDirectories {
    private BetterGitDirectories() {
    }

    public static Path root(Path projectPath) throws IOException {
        Path betterGitDirectory = projectPath.resolve(".bettergit");
        requireNonSymbolicDirectory(betterGitDirectory);
        Files.createDirectories(betterGitDirectory);
        return betterGitDirectory;
    }

    public static Path child(Path projectPath, String name) throws IOException {
        Path childDirectory = root(projectPath).resolve(name);
        requireNonSymbolicDirectory(childDirectory);
        Files.createDirectories(childDirectory);
        return childDirectory;
    }

    private static void requireNonSymbolicDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Refusing to write through symbolic BetterGit directory: " + directory);
        }
    }
}
