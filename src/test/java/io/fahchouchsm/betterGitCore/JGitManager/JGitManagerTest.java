package io.fahchouchsm.betterGitCore.JGitManager;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JGitManagerTest {
    private final JGitManager manager = new JGitManager();

    @TempDir
    Path temporaryFolder;

    @Test
    void reportsUntrackedAndStagedAndUnstagedChanges() throws Exception {
        try (Git git = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            Path file = temporaryFolder.resolve("notes.txt");
            Files.writeString(file, "first version");

            GitChanges untrackedChanges = manager.getChangesBeforeCommit(temporaryFolder);
            assertFalse(untrackedChanges.isClean());
            assertTrue(untrackedChanges.untracked().contains("notes.txt"));

            git.add().addFilepattern("notes.txt").call();
            GitChanges stagedChanges = manager.getChangesBeforeCommit(temporaryFolder);
            assertTrue(stagedChanges.added().contains("notes.txt"));
            assertTrue(stagedChanges.untracked().isEmpty());

            Files.writeString(file, "second version");
            GitChanges unstagedChanges = manager.getChangesBeforeCommit(temporaryFolder);
            assertTrue(unstagedChanges.added().contains("notes.txt"));
            assertTrue(unstagedChanges.modified().contains("notes.txt"));
        }
    }

    @Test
    void reportsCleanRepository() throws Exception {
        try (Git ignored = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            GitChanges changes = manager.getChangesBeforeCommit(temporaryFolder);

            assertTrue(changes.isClean());
        }
    }
}
