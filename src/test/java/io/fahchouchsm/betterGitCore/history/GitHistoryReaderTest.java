package io.fahchouchsm.betterGitCore.history;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHistoryReaderTest {
    private static final Instant FIRST_DATE = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant SECOND_DATE = Instant.parse("2026-08-11T11:00:00Z");

    @TempDir
    Path repositoryPath;

    @Test
    void readsRepositoryStateMetadataDecorationsAndCommitChanges() throws Exception {
        try (Git git = repositoryWithHistory()) {
            git.tag().setName("v1.0.0").call();
            Path reports = repositoryPath.resolve(".bettergit/reports");
            Files.createDirectories(reports);
            Files.writeString(repositoryPath.resolve(".bettergit/config.json"), "{}");
            Files.writeString(reports.resolve(git.getRepository().resolve("HEAD").name() + ".md"), "# Report\n");
            Files.writeString(repositoryPath.resolve("untracked.txt"), "local\n");

            RepositoryHistory history = new GitHistoryReader().read(repositoryPath, query(10));

            assertEquals(2, history.commits().size());
            assertFalse(history.summary().clean());
            assertEquals(3, history.summary().untrackedFiles());
            assertTrue(history.summary().betterGitInitialized());
            assertEquals(1, history.summary().betterGitReports());
            HistoryCommit newest = history.commits().getFirst();
            assertEquals("feat: update application", newest.subject());
            assertEquals("Bob", newest.author().name());
            assertEquals("UTF-8", newest.encoding());
            assertEquals(1, newest.statistics().filesChanged());
            assertEquals(3, newest.statistics().additions());
            assertEquals(1, newest.statistics().deletions());
            assertTrue(newest.decorations().stream().anyMatch(ref -> ref.startsWith("HEAD →")));
            assertTrue(newest.decorations().contains("tag: v1.0.0"));
            assertTrue(newest.betterGitReport().endsWith(newest.hash() + ".md"));
            assertFalse(newest.signed());
        }
    }

    @Test
    void filtersByAuthorMessageDateAndPathThenReversesSelection() throws Exception {
        try (Git ignored = repositoryWithHistory()) {
            GitHistoryReader reader = new GitHistoryReader();
            HistoryQuery filtered = new HistoryQuery(
                    10, false, "bob", "UPDATE", SECOND_DATE, SECOND_DATE,
                    "src/App.java", false, true);
            HistoryQuery reversed = new HistoryQuery(
                    10, false, null, null, null, null, null, true, true);

            RepositoryHistory matching = reader.read(repositoryPath, filtered);
            RepositoryHistory oldestFirst = reader.read(repositoryPath, reversed);

            assertEquals(1, matching.commits().size());
            assertEquals("feat: update application", matching.commits().getFirst().subject());
            assertEquals("chore: initial project", oldestFirst.commits().getFirst().subject());
        }
    }

    @Test
    void emptyRepositoryReturnsStateWithoutInventingCommits() throws Exception {
        try (Git ignored = Git.init().setDirectory(repositoryPath.toFile()).call()) {
            RepositoryHistory history = new GitHistoryReader().read(repositoryPath, query(20));

            assertTrue(history.commits().isEmpty());
            assertTrue(history.summary().clean());
        }
    }

    private Git repositoryWithHistory() throws Exception {
        Git git = Git.init().setDirectory(repositoryPath.toFile()).call();
        Path source = repositoryPath.resolve("src/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class App {}\n");
        commit(git, "chore: initial project", "Alice", FIRST_DATE);
        Files.writeString(source, "class App {\n    void run() {}\n}\n");
        commit(git, "feat: update application\n\nAdds runtime behavior.", "Bob", SECOND_DATE);
        return git;
    }

    private void commit(Git git, String message, String authorName, Instant time) throws Exception {
        git.add().addFilepattern(".").call();
        PersonIdent identity = new PersonIdent(authorName, authorName.toLowerCase() + "@example.com",
                time, ZoneOffset.UTC);
        git.commit().setMessage(message).setAuthor(identity).setCommitter(identity).call();
    }

    private static HistoryQuery query(int limit) {
        return new HistoryQuery(limit, false, null, null, null, null, null, false, true);
    }
}
