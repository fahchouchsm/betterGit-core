package io.fahchouchsm.betterGitCore.JGitManager;

import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitNoStagedChangesException;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void detectsRepositoriesAndGitWorktreeFiles() throws Exception {
        Path mainRepository = Files.createDirectory(temporaryFolder.resolve("main"));
        Path linkedWorktree = Files.createDirectory(temporaryFolder.resolve("linked"));
        try (Git git = Git.init().setDirectory(mainRepository.toFile()).call()) {
            Files.writeString(linkedWorktree.resolve(".git"),
                    "gitdir: " + git.getRepository().getDirectory().getAbsolutePath() + "\n");

            assertTrue(manager.hasRepository(mainRepository));
            assertTrue(manager.hasRepository(linkedWorktree));
            assertFalse(manager.hasRepository(temporaryFolder));
        }
    }

    @Test
    void initializesRepositoryWithoutCreatingACommit() {
        manager.initializeRepository(temporaryFolder);

        assertTrue(manager.hasRepository(temporaryFolder));
        try (Git git = Git.open(temporaryFolder.toFile())) {
            assertTrue(git.getRepository().resolve("HEAD") == null);
        } catch (Exception exception) {
            throw new AssertionError(exception);
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
    void returnsOnlyStagedContentForAiDocumentation() throws Exception {
        try (Git git = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            Path file = temporaryFolder.resolve("notes.txt");
            Files.writeString(file, "staged version\n");
            git.add().addFilepattern("notes.txt").call();
            Files.writeString(file, "unstaged version\n");

            String stagedCommitDiff = manager.getStagedCommitDiff(temporaryFolder);

            assertTrue(stagedCommitDiff.contains("+staged version"));
            assertFalse(stagedCommitDiff.contains("unstaged version"));
        }
    }

    @Test
    void rejectsDocumentationRequestsWithoutStagedChanges() throws Exception {
        try (Git ignored = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            assertThrows(GitNoStagedChangesException.class,
                    () -> manager.getStagedCommitDiff(temporaryFolder));
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

    @Test
    void commitsOnlyStagedChangesAndReturnsTheCommitHash() throws Exception {
        try (Git git = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            Files.writeString(temporaryFolder.resolve("staged.txt"), "staged\n");
            Files.writeString(temporaryFolder.resolve("untracked.txt"), "untracked\n");
            git.add().addFilepattern("staged.txt").call();

            String commitHash = manager.commitStagedChanges(temporaryFolder, "feat: staged commit");

            assertEquals(40, commitHash.length());
            assertEquals("feat: staged commit", git.log().call().iterator().next().getShortMessage());
            assertTrue(git.status().call().getUntracked().contains("untracked.txt"));
        }
    }

    @Test
    void readsAndCommitsTheContainingRepositoryFromANestedDirectory() throws Exception {
        Path nestedProject = Files.createDirectories(temporaryFolder.resolve("nested/project"));
        try (Git git = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            Files.writeString(temporaryFolder.resolve("staged.txt"), "staged from nested project\n");
            git.add().addFilepattern("staged.txt").call();

            GitChanges changes = manager.getChangesBeforeCommit(nestedProject);
            GitChangeDetails details = manager.getChangeDetailsBeforeCommit(nestedProject);
            String commitHash = manager.commitStagedChanges(nestedProject, "Document the nested project change.");

            assertTrue(changes.added().contains("staged.txt"));
            assertTrue(details.stagedDiff().contains("+staged from nested project"));
            assertEquals(40, commitHash.length());
            assertEquals("Document the nested project change.",
                    git.log().call().iterator().next().getShortMessage());
        }
    }

    @Test
    void initializesAnIndependentRepositoryInsideAParentRepository() throws Exception {
        Path childProject = Files.createDirectory(temporaryFolder.resolve("child"));
        try (Git ignored = Git.init().setDirectory(temporaryFolder.toFile()).call()) {
            assertFalse(manager.hasRepository(childProject));

            manager.initializeRepository(childProject);

            assertTrue(manager.hasRepository(childProject));
            assertTrue(Files.isDirectory(childProject.resolve(".git")));
        }
    }
}
