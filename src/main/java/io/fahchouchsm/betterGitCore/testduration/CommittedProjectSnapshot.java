package io.fahchouchsm.betterGitCore.testduration;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class CommittedProjectSnapshot implements AutoCloseable {
    private final Path snapshotRoot;
    private final Path projectDirectory;

    private CommittedProjectSnapshot(Path snapshotRoot, Path projectDirectory) {
        this.snapshotRoot = snapshotRoot;
        this.projectDirectory = projectDirectory;
    }

    static CommittedProjectSnapshot create(Path projectPath, String commitHash) throws IOException {
        Path snapshot = Files.createTempDirectory("bettergit-commit-" + commitHash.substring(0, 8) + "-");
        try (Repository repository = repository(projectPath)) {
            Path relativeProject = repository.getWorkTree().toPath().toAbsolutePath().normalize()
                    .relativize(projectPath.toAbsolutePath().normalize());
            copyCommit(repository, commitHash, snapshot);
            return new CommittedProjectSnapshot(snapshot, snapshot.resolve(relativeProject));
        } catch (IOException exception) {
            try {
                deleteRecursively(snapshot);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    Path directory() {
        return projectDirectory;
    }

    @Override
    public void close() throws IOException {
        deleteRecursively(snapshotRoot);
    }

    private static Repository repository(Path projectPath) throws IOException {
        return new FileRepositoryBuilder().findGitDir(projectPath.toFile()).build();
    }

    private static void copyCommit(Repository repository, String commitHash, Path snapshot) throws IOException {
        try (RevWalk revisions = new RevWalk(repository)) {
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                throw new IOException("Commit does not exist: " + commitHash);
            }
            RevCommit commit = revisions.parseCommit(commitId);
            copyTree(repository, commit, snapshot);
        }
    }

    private static void copyTree(Repository repository, RevCommit commit, Path snapshot) throws IOException {
        try (TreeWalk files = new TreeWalk(repository)) {
            files.addTree(commit.getTree());
            files.setRecursive(true);
            while (files.next()) {
                copyEntry(repository, files, snapshot);
            }
        }
    }

    private static void copyEntry(Repository repository, TreeWalk files, Path snapshot) throws IOException {
        Path destination = safeDestination(snapshot, files.getPathString());
        FileMode mode = files.getFileMode(0);
        if (FileMode.SYMLINK.equals(mode)) {
            copySymbolicLink(repository, files, snapshot, destination);
            return;
        }
        if (!FileMode.REGULAR_FILE.equals(mode) && !FileMode.EXECUTABLE_FILE.equals(mode)) {
            throw new IOException("Unsupported Git entry in committed test snapshot: " + files.getPathString());
        }
        copyFile(repository, files, destination);
    }

    private static Path safeDestination(Path snapshot, String repositoryPath) throws IOException {
        Path destination = snapshot.resolve(repositoryPath).normalize();
        if (!destination.startsWith(snapshot)) {
            throw new IOException("Committed path escapes the test snapshot: " + repositoryPath);
        }
        Files.createDirectories(destination.getParent());
        return destination;
    }

    private static void copyFile(Repository repository, TreeWalk files, Path destination) throws IOException {
        try (OutputStream output = Files.newOutputStream(destination)) {
            repository.open(files.getObjectId(0)).copyTo(output);
        }
        if (FileMode.EXECUTABLE_FILE.equals(files.getFileMode(0))
                && !destination.toFile().setExecutable(true, true)) {
            throw new IOException("Could not preserve executable file: " + files.getPathString());
        }
    }

    private static void copySymbolicLink(
            Repository repository, TreeWalk files, Path snapshot, Path destination) throws IOException {
        String linkText = new String(repository.open(files.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
        Path linkTarget = Path.of(linkText);
        Path resolvedTarget = destination.getParent().resolve(linkTarget).normalize();
        if (linkTarget.isAbsolute() || !resolvedTarget.startsWith(snapshot)) {
            throw new IOException("Symbolic link escapes the committed test snapshot: " + files.getPathString());
        }
        Files.createSymbolicLink(destination, linkTarget);
    }

    private static void deleteRecursively(Path snapshot) throws IOException {
        if (!Files.exists(snapshot)) {
            return;
        }
        List<Path> snapshotEntries;
        try (Stream<Path> discoveredEntries = Files.walk(snapshot)) {
            snapshotEntries = discoveredEntries.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path entry : snapshotEntries) {
            Files.deleteIfExists(entry);
        }
    }
}
