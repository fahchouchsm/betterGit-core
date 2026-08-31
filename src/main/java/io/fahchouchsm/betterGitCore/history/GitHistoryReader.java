package io.fahchouchsm.betterGitCore.history;

import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitRepositoryNotFoundException;
import io.fahchouchsm.betterGitCore.JGitManager.exceptions.GitStateReadException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class GitHistoryReader {
    public RepositoryHistory read(Path projectPath, HistoryQuery query) {
        Path repositoryPath = projectPath.toAbsolutePath().normalize();
        try (Git git = Git.open(repositoryPath.toFile())) {
            return readHistory(repositoryPath, git, query);
        } catch (RepositoryNotFoundException exception) {
            throw new GitRepositoryNotFoundException("Not a Git repository: " + repositoryPath, exception);
        } catch (IOException | GitAPIException exception) {
            throw new GitStateReadException("Could not read Git history from " + repositoryPath, exception);
        }
    }

    private static RepositoryHistory readHistory(Path repositoryPath, Git git, HistoryQuery query)
            throws IOException, GitAPIException {
        Repository repository = git.getRepository();
        RepositorySummary summary = repositorySummary(repositoryPath, git);
        if (repository.resolve(Constants.HEAD) == null) {
            return new RepositoryHistory(repositoryPath, summary, List.of());
        }
        Map<ObjectId, List<String>> decorations = decorations(repository, summary.branch());
        List<HistoryCommit> commits = commits(
                git, query, decorations, repositoryPath.resolve(".bettergit/reports"));
        if (query.reverse()) {
            Collections.reverse(commits);
        }
        return new RepositoryHistory(repositoryPath, summary, List.copyOf(commits));
    }

    private static RepositorySummary repositorySummary(Path repositoryPath, Git git)
            throws IOException, GitAPIException {
        Repository repository = git.getRepository();
        Status status = git.status().call();
        String fullBranch = repository.getFullBranch();
        boolean detached = fullBranch != null && !fullBranch.startsWith(Constants.R_HEADS);
        String branch = detached ? abbreviate(fullBranch) : repository.getBranch();
        TrackingSummary tracking = detached ? TrackingSummary.none() : tracking(repository, branch);
        Path betterGitDirectory = repositoryPath.resolve(".bettergit");
        return new RepositorySummary(
                repositoryName(repositoryPath), branch, detached,
                repository.getRepositoryState().getDescription(), status.isClean(),
                status.getAdded().size() + status.getChanged().size() + status.getRemoved().size(),
                status.getModified().size() + status.getMissing().size(), status.getUntracked().size(),
                status.getConflicting().size(), tracking.upstream(), tracking.ahead(), tracking.behind(),
                Files.isRegularFile(betterGitDirectory.resolve("config.json")),
                reportCount(betterGitDirectory.resolve("reports")));
    }

    private static List<HistoryCommit> commits(
            Git git,
            HistoryQuery query,
            Map<ObjectId, List<String>> decorations,
            Path reportsDirectory) throws GitAPIException, IOException {
        LogCommand log = configuredLog(git, query);
        try (DiffFormatter formatter = formatter(git.getRepository());
             RevWalk walk = new RevWalk(git.getRepository())) {
            CommitReadContext context = new CommitReadContext(
                    formatter, walk, decorations, reportsDirectory, query.includeChanges());
            return selectedCommits(log, query, context);
        }
    }

    private static LogCommand configuredLog(Git git, HistoryQuery query) throws IOException {
        LogCommand log = git.log();
        if (query.allReferences()) {
            log.all();
        }
        if (query.path() != null && !query.path().isBlank()) {
            log.addPath(query.path());
        }
        return log;
    }

    private static List<HistoryCommit> selectedCommits(
            LogCommand log, HistoryQuery query, CommitReadContext context)
            throws GitAPIException, IOException {
        List<HistoryCommit> commits = new ArrayList<>();
        for (RevCommit commit : log.call()) {
            if (matches(commit, query)) {
                commits.add(historyCommit(commit, context));
            }
            if (commits.size() == query.limit()) {
                break;
            }
        }
        return commits;
    }

    private static boolean matches(RevCommit commit, HistoryQuery query) {
        Instant committedAt = commit.getCommitterIdent().getWhenAsInstant();
        return (query.since() == null || !committedAt.isBefore(query.since()))
                && (query.until() == null || !committedAt.isAfter(query.until()))
                && contains(commit.getAuthorIdent().getName(), query.author())
                && contains(commit.getFullMessage(), query.message());
    }

    private static boolean contains(String candidate, String filter) {
        return filter == null || filter.isBlank()
                || candidate.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    private static HistoryCommit historyCommit(RevCommit commit, CommitReadContext context) throws IOException {
        List<CommitFileChange> files = context.includeChanges()
                ? fileChanges(commit, context.formatter(), context.walk()) : List.of();
        Path report = context.reportsDirectory().resolve(commit.name() + ".md");
        return new HistoryCommit(
                commit.name(), commit.getShortMessage(), commit.getFullMessage(),
                identity(commit.getAuthorIdent()), identity(commit.getCommitterIdent()),
                java.util.Arrays.stream(commit.getParents()).map(RevCommit::name).toList(),
                commit.getTree().name(), encoding(commit), commit.getFooterLines().stream()
                        .map(footer -> new CommitFooter(footer.getKey(), footer.getValue())).toList(),
                context.decorations().getOrDefault(commit, List.of()), commit.getRawGpgSignature() != null,
                Files.isRegularFile(report) ? report.toString() : null, statistics(files), files);
    }

    private static CommitIdentity identity(PersonIdent identity) {
        return new CommitIdentity(identity.getName(), identity.getEmailAddress(),
                identity.getWhenAsInstant(), identity.getZoneOffset().toString());
    }

    private static String encoding(RevCommit commit) {
        return commit.getEncodingName() == null ? StandardCharsets.UTF_8.name() : commit.getEncodingName();
    }

    private static List<CommitFileChange> fileChanges(
            RevCommit commit, DiffFormatter formatter, RevWalk walk) throws IOException {
        AbstractTreeIterator previousTree = previousTree(commit, walk);
        AbstractTreeIterator currentTree = new CanonicalTreeParser(
                null, walk.getObjectReader(), commit.getTree());
        List<CommitFileChange> files = new ArrayList<>();
        for (DiffEntry entry : formatter.scan(previousTree, currentTree)) {
            files.add(fileChange(entry, formatter.toFileHeader(entry)));
        }
        return List.copyOf(files);
    }

    private static AbstractTreeIterator previousTree(RevCommit commit, RevWalk walk) throws IOException {
        if (commit.getParentCount() == 0) {
            return new EmptyTreeIterator();
        }
        RevCommit parent = walk.parseCommit(commit.getParent(0));
        return new CanonicalTreeParser(null, walk.getObjectReader(), parent.getTree());
    }

    private static CommitFileChange fileChange(DiffEntry entry, FileHeader header) {
        int additions = header.toEditList().stream().mapToInt(edit -> edit.getLengthB()).sum();
        int deletions = header.toEditList().stream().mapToInt(edit -> edit.getLengthA()).sum();
        boolean binary = header.getPatchType() != FileHeader.PatchType.UNIFIED;
        String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath();
        String previousPath = entry.getChangeType() == DiffEntry.ChangeType.RENAME
                || entry.getChangeType() == DiffEntry.ChangeType.COPY ? entry.getOldPath() : null;
        return new CommitFileChange(entry.getChangeType().name(), path, previousPath, additions, deletions, binary);
    }

    private static CommitStatistics statistics(List<CommitFileChange> files) {
        return new CommitStatistics(
                files.size(), files.stream().mapToInt(CommitFileChange::additions).sum(),
                files.stream().mapToInt(CommitFileChange::deletions).sum(),
                (int) files.stream().filter(CommitFileChange::binary).count());
    }

    private static DiffFormatter formatter(Repository repository) {
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        formatter.setRepository(repository);
        formatter.setDetectRenames(true);
        return formatter;
    }

    private static Map<ObjectId, List<String>> decorations(Repository repository, String currentBranch)
            throws IOException {
        Map<ObjectId, List<String>> decorations = new LinkedHashMap<>();
        for (Ref reference : repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS)) {
            Ref peeled = repository.getRefDatabase().peel(reference);
            ObjectId target = peeled.getPeeledObjectId() == null ? peeled.getObjectId() : peeled.getPeeledObjectId();
            if (target != null) {
                decorations.computeIfAbsent(target, ignored -> new ArrayList<>())
                        .add(decoration(reference.getName(), currentBranch));
            }
        }
        return decorations;
    }

    private static String decoration(String reference, String currentBranch) {
        if (reference.equals(Constants.R_HEADS + currentBranch)) {
            return "HEAD → " + currentBranch;
        }
        if (reference.startsWith(Constants.R_HEADS)) {
            return "branch: " + Repository.shortenRefName(reference);
        }
        if (reference.startsWith(Constants.R_TAGS)) {
            return "tag: " + Repository.shortenRefName(reference);
        }
        if (reference.startsWith(Constants.R_REMOTES)) {
            return "remote: " + Repository.shortenRefName(reference);
        }
        return Repository.shortenRefName(reference);
    }

    private static String abbreviate(String objectId) {
        return objectId == null ? "unborn" : objectId.substring(0, Math.min(12, objectId.length()));
    }

    private static TrackingSummary tracking(Repository repository, String branch) throws IOException {
        BranchTrackingStatus status = BranchTrackingStatus.of(repository, branch);
        return status == null
                ? TrackingSummary.none()
                : new TrackingSummary(status.getRemoteTrackingBranch(), status.getAheadCount(), status.getBehindCount());
    }

    private static String repositoryName(Path repositoryPath) {
        Path fileName = repositoryPath.getFileName();
        return fileName == null ? repositoryPath.toString() : fileName.toString();
    }

    private static int reportCount(Path reportsDirectory) throws IOException {
        if (!Files.isDirectory(reportsDirectory)) {
            return 0;
        }
        try (Stream<Path> reports = Files.list(reportsDirectory)) {
            return (int) reports.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md")).count();
        }
    }

    private record CommitReadContext(
            DiffFormatter formatter,
            RevWalk walk,
            Map<ObjectId, List<String>> decorations,
            Path reportsDirectory,
            boolean includeChanges) {
    }

    private record TrackingSummary(String upstream, int ahead, int behind) {
        private static TrackingSummary none() {
            return new TrackingSummary(null, 0, 0);
        }
    }
}
