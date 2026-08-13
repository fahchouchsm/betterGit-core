package io.fahchouchsm.betterGitCore.JGitManager;

import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitInitializationException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitNoStagedChangesException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitRepositoryNotFoundException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitRepositoryPathException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitStateReadException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Provides repository detection, initialization, and read-only inspection through JGit. */
public final class JGitManager {

    /** Detects parent repositories and both directory- and file-based .git metadata. */
    public boolean isInsideRepository(Path projectPath) {
        Path repositoryPath = requireDirectory(projectPath);
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        repositoryBuilder.findGitDir(repositoryPath.toFile());
        return repositoryBuilder.getGitDir() != null;
    }

    /** Initializes a Git repository in the supplied directory without changing its configuration or contents. */
    public void initializeRepository(Path projectPath) {
        Path repositoryPath = requireDirectory(projectPath);
        try (Git ignored = Git.init().setDirectory(repositoryPath.toFile()).call()) {
            // Closing the JGit handle releases repository resources; initialization is already complete.
        } catch (GitAPIException exception) {
            throw new GitInitializationException("Could not initialize Git in " + repositoryPath, exception);
        }
    }

    /**
     * Returns changes that would affect the next commit.
     *
     * @param projectPath the root directory of an existing local Git repository
     * @throws GitRepositoryPathException when the path is invalid or cannot be accessed
     * @throws GitRepositoryNotFoundException when the directory is not a Git repository
     * @throws GitStateReadException when Git state cannot be read
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
            throw new GitRepositoryNotFoundException("Not a Git repository: " + repositoryPath, exception);
        } catch (IOException | GitAPIException exception) {
            throw new GitStateReadException("Could not read Git changes from " + repositoryPath, exception);
        }
    }

    /**
     * Returns unified diff text for the staged and unstaged changes in an existing repository.
     * Untracked files have no Git diff until they are staged; use {@link #getChangesBeforeCommit(Path)} for them.
     * Callers must redact sensitive content before sending these patches to an external service.
     */
    public GitChangeDetails getChangeDetailsBeforeCommit(Path projectPath) {
        Path repositoryPath = requireDirectory(projectPath);
        try (Git git = Git.open(repositoryPath.toFile())) {
            return new GitChangeDetails(createStagedDiff(git), createUnstagedDiff(git));
        } catch (RepositoryNotFoundException exception) {
            throw new GitRepositoryNotFoundException("Not a Git repository: " + repositoryPath, exception);
        } catch (IOException | GitAPIException exception) {
            throw new GitStateReadException("Could not read Git diffs from " + repositoryPath, exception);
        }
    }

    /**
     * Returns only the staged patch for the next commit, ready to send to a documentation AI.
     * Unstaged and untracked changes are deliberately excluded because they are not part of that commit.
     * Callers must redact sensitive content before sending this patch to an external service.
     *
     * @throws GitNoStagedChangesException when no changes are staged
     */
    public String getStagedCommitDiff(Path projectPath) {
        String stagedDiff = getChangeDetailsBeforeCommit(projectPath).stagedDiff();
        if (stagedDiff.isBlank()) {
            throw new GitNoStagedChangesException("Stage changes before generating commit documentation.");
        }
        return stagedDiff;
    }

    private String createStagedDiff(Git git) throws GitAPIException, IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Repository repository = git.getRepository();
            if (repository.resolve(Constants.HEAD) == null) {
                try (DiffFormatter formatter = new DiffFormatter(output)) {
                    formatter.setRepository(repository);
                    formatter.format(formatter.scan(
                            new EmptyTreeIterator(),
                            new DirCacheIterator(repository.readDirCache())));
                }
                return output.toString(StandardCharsets.UTF_8);
            }
            git.diff().setCached(true).setOutputStream(output).call();
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String createUnstagedDiff(Git git) throws GitAPIException, IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            git.diff().setCached(false).setOutputStream(output).call();
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private Path requireDirectory(Path projectPath) {
        if (projectPath == null) {
            throw new GitRepositoryPathException("projectPath must not be null");
        }
        try {
            Path normalizedPath = projectPath.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalizedPath)) {
                throw new GitRepositoryPathException("projectPath must be an existing directory: " + normalizedPath);
            }
            return normalizedPath;
        } catch (SecurityException exception) {
            throw new GitRepositoryPathException("Cannot access projectPath: " + projectPath, exception);
        }
    }
}
