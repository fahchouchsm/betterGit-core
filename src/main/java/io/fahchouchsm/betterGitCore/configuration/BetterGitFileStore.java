package io.fahchouchsm.betterGitCore.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes BetterGit project files without exposing secrets or partially replacing individual files. */
public final class BetterGitFileStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public void writeInitialization(
            Path projectPath, BetterGitConfiguration configuration, String generalDocumentation) throws IOException {
        Path betterGitDirectory = BetterGitDirectories.root(projectPath);
        Path generalFile = betterGitDirectory.resolve("general.md");
        if (!Files.isRegularFile(generalFile)) {
            AtomicFileWriter.write(generalFile, generalDocumentation);
        }
        AtomicFileWriter.write(
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
        AtomicFileWriter.write(gitIgnore, existingContent + separator + ".env" + System.lineSeparator());
    }

    public void ensureReportsIgnored(Path projectPath) throws IOException {
        Path gitIgnore = projectPath.resolve(".gitignore");
        String existingContent = Files.isRegularFile(gitIgnore) ? Files.readString(gitIgnore) : "";
        if (existingContent.lines().map(String::trim).anyMatch(this::definesReportPolicy)) {
            return;
        }
        String separator = existingContent.isEmpty() || existingContent.endsWith("\n")
                ? ""
                : System.lineSeparator();
        AtomicFileWriter.write(gitIgnore,
                existingContent + separator + ".bettergit/reports/" + System.lineSeparator());
    }

    public void prepareCommitReports(Path projectPath) throws IOException {
        BetterGitDirectories.child(projectPath, "reports");
    }

    private boolean ignoresEnvFile(String pattern) {
        return ".env".equals(pattern) || "/.env".equals(pattern);
    }

    private boolean definesReportPolicy(String pattern) {
        return ".bettergit/reports/".equals(pattern)
                || pattern.startsWith("!.bettergit/reports/");
    }

}
