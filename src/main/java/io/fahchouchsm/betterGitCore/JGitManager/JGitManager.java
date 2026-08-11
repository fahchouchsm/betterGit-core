package io.fahchouchsm.betterGitCore.JGitManager;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads the uncommitted state of an existing local Git repository without modifying it. */
public final class JGitManager {

    /**
     * Returns staged and unstaged changes that would affect the next commit.
     *
     * @param projectPath the root directory of an existing local Git repository
     * @throws IllegalArgumentException when the path is invalid or is not a Git repository
     * @throws IllegalStateException when Git state cannot be read
     */
    public GitChanges getChangesBeforeCommit(Path projectPath) {
        Path repositoryPath = requireDirectory(projectPath);
        try (Git git = Git.open(repositoryPath.toFile())) {
            Status status = git.status().call();
            return new GitChanges(
                    status.getAdded(),
                    status.getChanged(),
                    status.getRemoved(),
                    status.getModified(),
                    status.getMissing(),
                    status.getUntracked(),
                    status.getConflicting(),
                    status.getIgnoredNotInIndex());
        } catch (RepositoryNotFoundException exception) {
            throw new IllegalArgumentException("Not a Git repository: " + repositoryPath, exception);
        } catch (IOException | GitAPIException exception) {
            throw new IllegalStateException("Could not read Git changes from " + repositoryPath, exception);
        }
    }

    private Path requireDirectory(Path projectPath) {
        Objects.requireNonNull(projectPath, "projectPath must not be null");
        Path normalizedPath = projectPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedPath)) {
            throw new IllegalArgumentException("projectPath must be an existing directory: " + normalizedPath);
        }
        return normalizedPath;
    }
}
