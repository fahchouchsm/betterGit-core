package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.JGitManager.GitChanges;
import io.fahchouchsm.betterGitCore.JGitManager.JGitManager;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JGitCommitDataSource implements CommitDataSource {
    private static final Pattern RENAMED_PATHS = Pattern.compile(
            "(?m)^rename from (.+)$\\R^rename to (.+)$");
    private final JGitManager git;

    public JGitCommitDataSource(JGitManager git) {
        this.git = git;
    }

    @Override
    public CommitSnapshot stagedSnapshot(Path projectPath) {
        GitChanges changes = git.getChangesBeforeCommit(projectPath);
        String stagedDiff = git.getChangeDetailsBeforeCommit(projectPath).stagedDiff();
        List<ChangedFile> files = stagedFiles(changes, stagedDiff);
        return new CommitSnapshot(
                git.getCurrentBranch(projectPath),
                files,
                stagedDiff,
                DiffStatistics.fromDiff(files.size(), stagedDiff),
                "Validation was not run or provided.");
    }

    private static List<ChangedFile> stagedFiles(GitChanges changes, String stagedDiff) {
        Map<String, ChangedFile> files = new LinkedHashMap<>();
        changes.added().forEach(path -> files.put(path, ChangedFile.added(path)));
        changes.changed().forEach(path -> files.put(path, ChangedFile.modified(path)));
        changes.removed().forEach(path -> files.put(path, ChangedFile.deleted(path)));
        applyRenames(files, stagedDiff);
        return files.values().stream()
                .sorted(Comparator.comparing(ChangedFile::path))
                .toList();
    }

    private static void applyRenames(Map<String, ChangedFile> files, String stagedDiff) {
        Matcher matcher = RENAMED_PATHS.matcher(stagedDiff);
        while (matcher.find()) {
            String previousPath = matcher.group(1);
            String path = matcher.group(2);
            files.remove(previousPath);
            files.remove(path);
            files.put(path, ChangedFile.renamed(previousPath, path));
        }
    }

}
