package io.fahchouchsm.betterGitCore.JGitManager;

import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitRepositoryNotFoundException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitRepositoryPathException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            GitChangeDetails details = manager.getChangeDetailsBeforeCommit(temporaryFolder);
            assertTrue(details.stagedDiff().contains("+first version"));
            assertTrue(details.unstagedDiff().contains("+second version"));
        }
    }

    @Test
    void reportsCleanRepository() throws Exception {
        try (Git ignored = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            GitChanges changes = manager.getChangesBeforeCommit(temporaryFolder);

            assertTrue(changes.isClean());
        }
    }

    @Test
    void returnsTheActualStagedAndUnstagedDiffs() throws Exception {
        try (Git git = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            Path file = temporaryFolder.resolve("notes.txt");
            Files.writeString(file, "original\n");
            git.add().addFilepattern("notes.txt").call();
            PersonIdent author = new PersonIdent("Test Author", "author@example.com");
            git.commit().setMessage("Initial commit").setAuthor(author).setCommitter(author).call();

            Files.writeString(file, "staged change\n");
            git.add().addFilepattern("notes.txt").call();
            Files.writeString(file, "unstaged change\n");

            GitChangeDetails details = manager.getChangeDetailsBeforeCommit(temporaryFolder);

            assertTrue(details.stagedDiff().contains("-original"));
            assertTrue(details.stagedDiff().contains("+staged change"));
            assertTrue(details.unstagedDiff().contains("-staged change"));
            assertTrue(details.unstagedDiff().contains("+unstaged change"));

        }
    }

    @Test
    void returnsStagedDiffForRepositoryWithoutFirstCommit() throws Exception {
        try (Git git = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            Files.writeString(temporaryFolder.resolve("first-file.txt"), "first content\n");
            git.add().addFilepattern("first-file.txt").call();

            GitChangeDetails details = manager.getChangeDetailsBeforeCommit(temporaryFolder);

            assertTrue(details.stagedDiff().contains("new file mode"));
            assertTrue(details.stagedDiff().contains("+first content"));
        }
    }

    @Test
    void reportsClearExceptionsForInvalidPathsAndNonRepositories() throws Exception {
        Path nonRepository = Files.createDirectory(temporaryFolder.resolve("not-a-repository"));

        assertThrows(GitRepositoryPathException.class, () -> manager.getChangesBeforeCommit(null));
        assertThrows(GitRepositoryPathException.class,
                () -> manager.getChangesBeforeCommit(temporaryFolder.resolve("does-not-exist")));
        assertThrows(GitRepositoryNotFoundException.class, () -> manager.getChangesBeforeCommit(nonRepository));
        assertThrows(GitRepositoryNotFoundException.class, () -> manager.getChangeDetailsBeforeCommit(nonRepository));
    }

    @Test
    void rejectsNullDiffs() {
        assertThrows(IllegalArgumentException.class, () -> new GitChangeDetails(null, ""));
        assertThrows(IllegalArgumentException.class, () -> new GitChangeDetails("", null));
    }
}
