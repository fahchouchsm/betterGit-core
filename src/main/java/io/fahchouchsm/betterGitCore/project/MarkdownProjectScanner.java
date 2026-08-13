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
import java.util.Set;

/** Collects bounded Markdown context for project documentation generation. */
public final class MarkdownProjectScanner {
    private static final int MAX_FILES = 40;
    private static final int MAX_FILE_CHARACTERS = 32_000;
    private static final int MAX_TOTAL_CHARACTERS = 200_000;
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".bettergit", "target", "build", "node_modules", "vendor", ".gradle");

    public String scan(Path projectPath) throws IOException {
        List<Path> markdownFiles = markdownFiles(projectPath);
        StringBuilder markdownContext = new StringBuilder();
        for (Path markdownFile : markdownFiles.stream().limit(MAX_FILES).toList()) {
            appendWithinLimit(markdownContext, projectPath, markdownFile);
            if (markdownContext.length() >= MAX_TOTAL_CHARACTERS) {
                break;
            }
        }
        return markdownContext.toString();
    }

    private static List<Path> markdownFiles(Path projectPath) throws IOException {
        List<Path> markdownFiles = new ArrayList<>();
        Files.walkFileTree(projectPath, new MarkdownFileVisitor(projectPath, markdownFiles));
        markdownFiles.sort(Comparator.comparing(path -> projectPath.relativize(path).toString()));
        return markdownFiles;
    }

    private static void appendWithinLimit(StringBuilder context, Path projectPath, Path markdownFile)
            throws IOException {
        int remainingCharacters = MAX_TOTAL_CHARACTERS - context.length();
        if (remainingCharacters <= 0) {
            return;
        }
        String heading = "\n--- FILE: " + projectPath.relativize(markdownFile) + " ---\n";
        if (remainingCharacters <= heading.length()) {
            return;
        }
        context.append(heading);
        int contentLimit = Math.min(MAX_FILE_CHARACTERS, MAX_TOTAL_CHARACTERS - context.length());
        context.append(readCharacters(markdownFile, contentLimit));
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
            if (attributes.isRegularFile() && file.getFileName().toString().toLowerCase().endsWith(".md")) {
                markdownFiles.add(file);
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
