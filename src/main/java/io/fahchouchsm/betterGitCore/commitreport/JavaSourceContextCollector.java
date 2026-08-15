package io.fahchouchsm.betterGitCore.commitreport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaSourceContextCollector {
    private static final long MAX_SOURCE_FILE_BYTES = 256_000;
    private static final int MAX_SOURCE_CONTEXT_CHARACTERS = 40_000;
    private static final int CONTEXT_RADIUS = 4;
    private static final Pattern DECLARATION = Pattern.compile(
            "^\\s*(package |import |(?:public |protected |private )?"
                    + "(?:(?:abstract|final|sealed|non-sealed) )*(?:class|interface|record|enum) )");
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");

    public String collect(Path projectPath, List<ChangedFile> changedFiles, String safeDiff) throws IOException {
        StringBuilder sourceContext = new StringBuilder();
        for (ChangedFile changedFile : changedFiles) {
            if (isReadableJavaChange(projectPath, changedFile)) {
                appendFileContext(sourceContext, projectPath, changedFile.path(), safeDiff);
                if (sourceContext.length() >= MAX_SOURCE_CONTEXT_CHARACTERS) {
                    return sourceContext.substring(0, MAX_SOURCE_CONTEXT_CHARACTERS);
                }
            }
        }
        return sourceContext.toString();
    }

    private static boolean isReadableJavaChange(Path projectPath, ChangedFile changedFile) throws IOException {
        if (changedFile.status() == ChangeStatus.DELETED || !changedFile.path().endsWith(".java")) {
            return false;
        }
        Path sourceFile = projectPath.resolve(changedFile.path()).normalize();
        return sourceFile.startsWith(projectPath)
                && Files.isRegularFile(sourceFile)
                && Files.size(sourceFile) <= MAX_SOURCE_FILE_BYTES;
    }

    private static void appendFileContext(
            StringBuilder sourceContext, Path projectPath, String relativePath, String diff) throws IOException {
        List<String> sourceLines = Files.readAllLines(projectPath.resolve(relativePath));
        boolean[] included = includedLines(sourceLines.size(), hunkRanges(diff, relativePath));
        sourceContext.append("\n### ").append(relativePath).append('\n');
        for (int index = 0; index < sourceLines.size(); index++) {
            String line = sourceLines.get(index);
            if (included[index] || DECLARATION.matcher(line).find()) {
                sourceContext.append(index + 1).append(": ").append(line).append('\n');
            }
        }
    }

    private static boolean[] includedLines(int lineCount, List<HunkRange> hunkRanges) {
        boolean[] included = new boolean[lineCount];
        for (HunkRange hunkRange : hunkRanges) {
            int start = Math.max(0, hunkRange.startLine() - 1 - CONTEXT_RADIUS);
            int end = Math.min(lineCount, hunkRange.startLine() + hunkRange.lineCount() + CONTEXT_RADIUS);
            for (int index = start; index < end; index++) {
                included[index] = true;
            }
        }
        return included;
    }

    private static List<HunkRange> hunkRanges(String diff, String relativePath) {
        List<HunkRange> ranges = new ArrayList<>();
        boolean matchingFile = false;
        for (String line : diff.lines().toList()) {
            if (line.startsWith("diff --git ")) {
                matchingFile = line.endsWith(" b/" + relativePath);
            } else if (matchingFile) {
                Matcher matcher = HUNK.matcher(line);
                if (matcher.find()) {
                    int lineCount = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
                    ranges.add(new HunkRange(Integer.parseInt(matcher.group(1)), lineCount));
                }
            }
        }
        return ranges;
    }

    private record HunkRange(int startLine, int lineCount) {
    }
}
