package io.fahchouchsm.betterGitCore.project;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Collects bounded Markdown context for project documentation generation. */
public final class MarkdownProjectScanner {
    private static final int MAX_FILES = 40;
    private static final int MAX_FILE_CHARACTERS = 32_000;
    private static final int MAX_TOTAL_CHARACTERS = 200_000;
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".bettergit", "target", "build", "node_modules", "vendor", ".gradle");

    public MarkdownScanResult scanProject(Path projectPath) throws IOException {
        List<Path> markdownFiles = markdownFiles(projectPath);
        StringBuilder markdownContext = new StringBuilder();
        int includedFiles = 0;
        int truncatedFiles = 0;
        int candidateCount = Math.min(markdownFiles.size(), MAX_FILES);
        for (int index = 0; index < candidateCount; index++) {
            MarkdownSection section = markdownSection(
                    projectPath,
                    markdownFiles.get(index),
                    MAX_TOTAL_CHARACTERS - markdownContext.length());
            if (!section.included()) {
                break;
            }
            markdownContext.append(section.markdownText());
            includedFiles++;
            if (section.truncated()) {
                truncatedFiles++;
            }
            if (markdownContext.length() >= MAX_TOTAL_CHARACTERS) {
                break;
            }
        }
        return new MarkdownScanResult(
                markdownContext.toString(),
                includedFiles,
                markdownFiles.size() - includedFiles,
                truncatedFiles);
    }

    private static List<Path> markdownFiles(Path projectPath) throws IOException {
        List<Path> markdownFiles = new ArrayList<>();
        Files.walkFileTree(projectPath, new MarkdownFileVisitor(projectPath, markdownFiles));
        markdownFiles.sort(Comparator.comparing(path -> projectPath.relativize(path).toString()));
        return markdownFiles;
    }

    private static MarkdownSection markdownSection(
            Path projectPath, Path markdownFile, int remainingCharacters)
            throws IOException {
        String heading = "\n--- FILE: " + projectPath.relativize(markdownFile) + " ---\n";
        if (remainingCharacters <= heading.length()) {
            return MarkdownSection.NOT_INCLUDED;
        }
        int contentLimit = Math.min(MAX_FILE_CHARACTERS, remainingCharacters - heading.length());
        String content = readCharacters(markdownFile, contentLimit + 1);
        boolean truncated = content.length() > contentLimit;
        String boundedContent = truncated ? content.substring(0, contentLimit) : content;
        return new MarkdownSection(heading + boundedContent, true, truncated);
    }

    private record MarkdownSection(String markdownText, boolean included, boolean truncated) {
        private static final MarkdownSection NOT_INCLUDED = new MarkdownSection("", false, false);
    }

    private static String readCharacters(Path markdownFile, int characterLimit) throws IOException {
        StringBuilder fileContent = new StringBuilder(characterLimit);
        char[] buffer = new char[Math.min(4_096, characterLimit)];
        try (Reader reader = Files.newBufferedReader(markdownFile, StandardCharsets.UTF_8)) {
            while (fileContent.length() < characterLimit) {
                int requested = Math.min(buffer.length, characterLimit - fileContent.length());
                int charactersRead = reader.read(buffer, 0, requested);
                if (charactersRead < 0) {
                    break;
                }
                fileContent.append(buffer, 0, charactersRead);
            }
        }
        return fileContent.toString();
    }

    private static final class MarkdownFileVisitor extends SimpleFileVisitor<Path> {
        private final Path projectPath;
        private final List<Path> markdownFiles;

        private MarkdownFileVisitor(Path projectPath, List<Path> markdownFiles) {
            this.projectPath = projectPath;
            this.markdownFiles = markdownFiles;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (!projectPath.equals(directory) && EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isRegularFile()
                    && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md")) {
                markdownFiles.add(file);
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
