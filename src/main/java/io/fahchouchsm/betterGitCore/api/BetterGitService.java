package io.fahchouchsm.betterGitCore.api;

import io.fahchouchsm.betterGitCore.JGitManager.GitChanges;
import io.fahchouchsm.betterGitCore.JGitManager.JGitManager;
import io.fahchouchsm.betterGitCore.commands.CommandRunner;
import io.fahchouchsm.betterGitCore.commands.CommandRuntime;
import io.fahchouchsm.betterGitCore.commands.JGitRepositoryAccess;
import io.fahchouchsm.betterGitCore.commitreport.JGitCommitDataSource;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.AiCredentialStore;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.configuration.FeatureStoragePreparer;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeCredentialStore;
import io.fahchouchsm.betterGitCore.documentation.AiSystemTextGenerator;
import io.fahchouchsm.betterGitCore.history.GitHistoryReader;
import io.fahchouchsm.betterGitCore.history.HistoryQuery;
import io.fahchouchsm.betterGitCore.history.RepositoryHistory;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

public final class BetterGitService {
    private final JGitManager git = new JGitManager();
    private final GitIndex index = new GitIndex();
    private final BetterGitConfigurationLoader configurationLoader = new BetterGitConfigurationLoader();
    private final BetterGitFileStore fileStore = new BetterGitFileStore();

    public RepositoryStatus status(Path repository) throws IOException {
        Path projectPath = repository(repository);
        GitChanges changes = git.getChangesBeforeCommit(projectPath);
        String repositoryName = projectPath.getFileName() == null
                ? projectPath.toString() : projectPath.getFileName().toString();
        return new RepositoryStatus(
                projectPath, repositoryName, git.getCurrentBranch(projectPath),
                configurationLoader.load(projectPath).isPresent(), staged(changes), unstaged(changes));
    }

    public List<String> localBranches(Path repository) throws IOException {
        Path projectPath = repository(repository);
        try (Git openedRepository = Git.open(projectPath.toFile())) {
            return openedRepository.branchList().call().stream()
                    .map(reference -> Repository.shortenRefName(reference.getName()))
                    .sorted()
                    .toList();
        } catch (GitAPIException exception) {
            throw new IOException("Could not read local branches.", exception);
        }
    }

    public RepositoryHistory history(Path repository, int limit) throws IOException {
        return new GitHistoryReader().read(repository(repository), HistoryQuery.latest(limit));
    }

    public Optional<BetterGitConfiguration> configuration(Path repository) throws IOException {
        return configurationLoader.load(repository(repository));
    }

    public AiSettingsView aiSettings(Path repository) throws IOException {
        Path projectPath = repository(repository);
        BetterGitConfiguration betterGit = configurationLoader.load(projectPath)
                .orElseThrow(() -> new IOException("Initialize BetterGit before loading AI settings."));
        AiConfiguration ai = new AiConfigurationLoader().load(
                projectPath, System.getenv(), betterGit.ai().model());
        return new AiSettingsView(
                ai.provider(), ai.model(), ai.apiUrl(), ai.apiKey() != null && !ai.apiKey().isBlank(),
                betterGit.ai().commitReportEnabled(), betterGit.ai().memoryEnabled());
    }

    public void stage(Path repository, List<String> paths) throws IOException, InterruptedException {
        index.stage(repository(repository), paths);
    }

    public void unstage(Path repository, List<String> paths) throws IOException, InterruptedException {
        index.unstage(repository(repository), paths);
    }

    public OperationOutcome initialize(Path repository, OperationObserver observer) {
        return execute(repository, observer, "init", "--yes");
    }

    public OperationOutcome commit(
            Path repository, CommitRequest request, OperationObserver observer) {
        List<String> arguments = new ArrayList<>(List.of("commit"));
        if (!request.useAiReport()) {
            arguments.add("--no-ai");
        }
        if (!request.message().isEmpty()) {
            arguments.add("--message");
            arguments.add(request.message());
        }
        return execute(repository, observer, arguments.toArray(String[]::new));
    }

    public OperationOutcome merge(
            Path repository, MergeRequest request, OperationObserver observer) {
        List<String> arguments = new ArrayList<>(List.of("merge", request.sourceBranch()));
        if (!request.message().isEmpty()) {
            arguments.add("--message");
            arguments.add(request.message());
        }
        return execute(repository, observer, arguments.toArray(String[]::new));
    }

    public void saveFeatureSettings(
            Path repository, FeatureSettings settings, String sonarToken) throws IOException {
        Path projectPath = repository(repository);
        BetterGitConfiguration configuration = configurationLoader.load(projectPath)
                .orElseThrow(() -> new IOException("Initialize BetterGit before saving feature settings."));
        fileStore.writeConfiguration(projectPath, configuration.withSettings(settings));
        new FeatureStoragePreparer(fileStore).prepare(projectPath, settings);
        saveSonarToken(projectPath, sonarToken);
    }

    public void saveAiSettings(Path repository, AiSettingsRequest request) throws IOException {
        Path projectPath = repository(repository);
        BetterGitConfiguration configuration = configurationLoader.load(projectPath)
                .orElseThrow(() -> new IOException("Initialize BetterGit before saving AI settings."));
        ensureAiCredential(projectPath, request);
        new AiCredentialStore().update(projectPath, aiEnvironment(request));
        fileStore.ensureEnvIgnored(projectPath);
        AiCommitSettings ai = new AiCommitSettings(
                request.commitReports(), request.memory(), request.model());
        fileStore.writeConfiguration(projectPath, configuration.withAiSettings(ai));
    }

    private void ensureAiCredential(Path projectPath, AiSettingsRequest request) throws IOException {
        if (!request.commitReports() || !request.apiKey().isBlank()) {
            return;
        }
        AiConfiguration current = new AiConfigurationLoader().load(projectPath, System.getenv());
        if (current.apiKey() == null || current.apiKey().isBlank()) {
            throw new IOException("An AI API key is required when commit reports are enabled.");
        }
    }

    private static Map<String, String> aiEnvironment(AiSettingsRequest request) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put(AiConfigurationLoader.API_PROVIDER, request.provider().setting());
        settings.put(AiConfigurationLoader.API_MODEL, request.model());
        settings.put(AiConfigurationLoader.API_URL, request.apiUrl());
        if (!request.apiKey().isBlank()) {
            settings.put(AiConfigurationLoader.API_KEY, request.apiKey());
        }
        return Map.copyOf(settings);
    }

    private void saveSonarToken(Path projectPath, String sonarToken) throws IOException {
        if (sonarToken == null || sonarToken.isBlank()) {
            return;
        }
        new SonarQubeCredentialStore().update(projectPath, sonarToken.strip());
        fileStore.ensureEnvIgnored(projectPath);
    }

    private OperationOutcome execute(
            Path repository, OperationObserver observer, String... arguments) {
        Path projectPath = repository(repository);
        ApiConsole console = new ApiConsole(observer);
        return new OperationOutcome(CommandRunner.execute(arguments, runtime(projectPath, console)));
    }

    private CommandRuntime runtime(Path projectPath, ApiConsole console) {
        return new CommandRuntime(
                projectPath, console, new JGitRepositoryAccess(git), new JGitCommitDataSource(git),
                git::commitStagedChanges, System.getenv(), Clock.systemUTC(), new AiSystemTextGenerator());
    }

    private static Path repository(Path candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("Repository directory is required.");
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Repository directory does not exist: " + normalized);
        }
        return normalized;
    }

    private static List<ChangedPath> staged(GitChanges changes) {
        return Stream.of(
                        changedPaths(changes.added(), ChangeKind.ADDED),
                        changedPaths(changes.changed(), ChangeKind.MODIFIED),
                        changedPaths(changes.removed(), ChangeKind.DELETED))
                .flatMap(List::stream).sorted().toList();
    }

    private static List<ChangedPath> unstaged(GitChanges changes) {
        return Stream.of(
                        changedPaths(changes.modified(), ChangeKind.MODIFIED),
                        changedPaths(changes.missing(), ChangeKind.DELETED),
                        changedPaths(changes.untracked(), ChangeKind.UNTRACKED),
                        changedPaths(changes.conflicting(), ChangeKind.CONFLICTING))
                .flatMap(List::stream).sorted().toList();
    }

    private static List<ChangedPath> changedPaths(Iterable<String> paths, ChangeKind kind) {
        List<ChangedPath> changed = new ArrayList<>();
        paths.forEach(path -> changed.add(new ChangedPath(path, kind)));
        return changed;
    }
}
