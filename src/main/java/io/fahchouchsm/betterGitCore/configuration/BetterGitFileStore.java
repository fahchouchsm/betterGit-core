package io.fahchouchsm.betterGitCore.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

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
        writeConfiguration(projectPath, configuration);
    }

    public void writeConfiguration(Path projectPath, BetterGitConfiguration configuration) throws IOException {
        Path betterGitDirectory = BetterGitDirectories.root(projectPath);
        AtomicFileWriter.write(betterGitDirectory.resolve("config.json"),
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
        ensureIgnored(projectPath, ".bettergit/reports/", this::definesReportPolicy);
    }

    public void ensureDiagramsIgnored(Path projectPath) throws IOException {
        ensureIgnored(projectPath, ".bettergit/diagrams/", ".bettergit/diagrams/"::equals);
    }

    public void ensureTestDurationsIgnored(Path projectPath) throws IOException {
        ensureIgnored(projectPath, ".bettergit/test-durations/", ".bettergit/test-durations/"::equals);
    }

    private void ensureIgnored(
            Path projectPath,
            String ignoredPattern,
            Predicate<String> definesPolicy) throws IOException {
        Path gitIgnore = projectPath.resolve(".gitignore");
        String existingContent = Files.isRegularFile(gitIgnore) ? Files.readString(gitIgnore) : "";
        if (existingContent.lines().map(String::trim).anyMatch(definesPolicy)) {
            return;
        }
        String separator = existingContent.isEmpty() || existingContent.endsWith("\n")
                ? ""
                : System.lineSeparator();
        AtomicFileWriter.write(gitIgnore,
                existingContent + separator + ignoredPattern + System.lineSeparator());
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
