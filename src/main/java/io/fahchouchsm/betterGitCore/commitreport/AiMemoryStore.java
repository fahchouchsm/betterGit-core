package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.configuration.AtomicFileWriter;
import io.fahchouchsm.betterGitCore.configuration.BetterGitDirectories;
import io.fahchouchsm.betterGitCore.configuration.Utf8TextReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class AiMemoryStore {
    public static final int MAX_HISTORY_ENTRIES = 10;
    private static final int MAX_MEMORY_FILE_CHARACTERS = 200_000;
    private static final Pattern HISTORY_ENTRY = Pattern.compile("(?m)(?=^## )");
    private final ProjectMapScanner projectMapScanner;

    public AiMemoryStore(ProjectMapScanner projectMapScanner) {
        this.projectMapScanner = projectMapScanner;
    }

    public void initialize(Path projectPath) throws IOException {
        BetterGitDirectories.child(projectPath, "context");
        BetterGitDirectories.child(projectPath, "reports");
        refreshProjectMap(projectPath);
        createGeneralWhenMissing(projectPath);
        createHistoryWhenMissing(projectPath);
    }

    public MemoryContext read(Path projectPath, int historyLimit) throws IOException {
        return new MemoryContext(
                readIfPresent(projectPath.resolve(".bettergit/general.md")),
                readIfPresent(contextDirectory(projectPath).resolve("project-map.json")),
                recentHistory(projectPath, historyLimit));
    }

    public void refreshProjectMap(Path projectPath) throws IOException {
        Path projectMap = contextDirectory(projectPath).resolve("project-map.json");
        Files.createDirectories(projectMap.getParent());
        String currentMap = projectMapScanner.scan(projectPath);
        if (!currentMap.equals(readIfPresent(projectMap))) {
            AtomicFileWriter.write(projectMap, currentMap);
        }
    }

    public void appendHistory(Path projectPath, HistoryEntry entry) throws IOException {
        Path historyFile = contextDirectory(projectPath).resolve("recent-history.md");
        Files.createDirectories(historyFile.getParent());
        List<String> entries = new ArrayList<>();
        entries.add(entry.markdown());
        for (String existingEntry : historyEntries(readIfPresent(historyFile))) {
            if (!sameSummary(existingEntry, entry) && entries.size() < MAX_HISTORY_ENTRIES) {
                entries.add(existingEntry);
            }
        }
        AtomicFileWriter.write(historyFile, String.join("\n\n---\n\n", entries) + System.lineSeparator());
    }

    private static boolean sameSummary(String existingEntry, HistoryEntry newEntry) {
        return existingEntry.contains("**Title:** " + newEntry.title())
                && existingEntry.contains("**Summary:** " + newEntry.summary());
    }

    private String recentHistory(Path projectPath, int limit) throws IOException {
        Path historyFile = contextDirectory(projectPath).resolve("recent-history.md");
        List<String> entries = historyEntries(readIfPresent(historyFile));
        return String.join("\n\n---\n\n", entries.stream().limit(limit).toList());
    }

    private static List<String> historyEntries(String markdown) {
        if (markdown.isBlank()) {
            return List.of();
        }
        return HISTORY_ENTRY.splitAsStream(markdown)
                .map(String::strip)
                .filter(entry -> entry.startsWith("## "))
                .map(entry -> entry.replaceFirst("(?s)\\R---\\s*$", "").strip())
                .toList();
    }

    private static void createHistoryWhenMissing(Path projectPath) throws IOException {
        Path historyFile = contextDirectory(projectPath).resolve("recent-history.md");
        if (!Files.exists(historyFile)) {
            AtomicFileWriter.write(historyFile, "");
        }
    }

    private static void createGeneralWhenMissing(Path projectPath) throws IOException {
        Path generalFile = projectPath.resolve(".bettergit/general.md");
        if (Files.isRegularFile(generalFile)) {
            return;
        }
        String buildSystem = Files.isRegularFile(projectPath.resolve("pom.xml"))
                ? "Maven"
                : Files.isRegularFile(projectPath.resolve("build.gradle"))
                || Files.isRegularFile(projectPath.resolve("build.gradle.kts")) ? "Gradle" : "Not detected";
        String summary = """
                # BetterGit Project Overview

                - Project: %s
                - Build system: %s
                - Architecture map: `.bettergit/context/project-map.json`
                """.formatted(projectPath.getFileName(), buildSystem);
        AtomicFileWriter.write(generalFile, summary);
    }

    private static Path contextDirectory(Path projectPath) {
        return projectPath.resolve(".bettergit/context");
    }

    private static String readIfPresent(Path file) throws IOException {
        return Files.isRegularFile(file)
                ? Utf8TextReader.readPrefix(file, MAX_MEMORY_FILE_CHARACTERS)
                : "";
    }
}
