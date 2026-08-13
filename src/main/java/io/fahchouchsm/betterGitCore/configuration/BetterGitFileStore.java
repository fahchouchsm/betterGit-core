package io.fahchouchsm.betterGitCore.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Writes BetterGit project files without exposing secrets or partially replacing individual files. */
public final class BetterGitFileStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public void writeInitialization(
            Path projectPath, BetterGitConfiguration configuration, String generalDocumentation) throws IOException {
        Path betterGitDirectory = projectPath.resolve(".bettergit");
        if (Files.isSymbolicLink(betterGitDirectory)) {
            throw new IOException("Refusing to write through a symbolic .bettergit directory.");
        }
        Files.createDirectories(betterGitDirectory);
        atomicWrite(betterGitDirectory.resolve("general.md"), generalDocumentation);
        atomicWrite(
                betterGitDirectory.resolve("config.json"),
                JSON.toJson(configuration) + System.lineSeparator());
    }

    public void ensureEnvIgnored(Path projectPath) throws IOException {
        Path gitIgnore = projectPath.resolve(".gitignore");
        String existingContent = Files.isRegularFile(gitIgnore) ? Files.readString(gitIgnore) : "";
        List<String> existingLines = existingContent.lines().toList();
        if (existingLines.stream().map(String::trim).anyMatch(this::ignoresEnvFile)) {
            return;
        }
        String separator = existingContent.isEmpty() || existingContent.endsWith("\n") ? "" : System.lineSeparator();
        atomicWrite(gitIgnore, existingContent + separator + ".env" + System.lineSeparator());
    }

    private boolean ignoresEnvFile(String pattern) {
        return ".env".equals(pattern) || "/.env".equals(pattern);
    }

    private static void atomicWrite(Path destination, String content) throws IOException {
        Path temporaryFile = temporaryFileFor(destination);
        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);
            replaceAtomically(temporaryFile, destination);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static Path temporaryFileFor(Path destination) throws IOException {
        return Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
    }

    private static void replaceAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
