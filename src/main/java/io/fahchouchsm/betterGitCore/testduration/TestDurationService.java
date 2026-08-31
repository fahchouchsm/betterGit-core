package io.fahchouchsm.betterGitCore.testduration;

import io.fahchouchsm.betterGitCore.configuration.AtomicFileWriter;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitDirectories;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public final class TestDurationService {
    private final TestSuiteRunner testSuiteRunner;
    private final BetterGitConfigurationLoader configurationLoader;
    private final BetterGitFileStore fileStore;

    public TestDurationService(
            TestSuiteRunner testSuiteRunner,
            BetterGitConfigurationLoader configurationLoader,
            BetterGitFileStore fileStore) {
        this.testSuiteRunner = testSuiteRunner;
        this.configurationLoader = configurationLoader;
        this.fileStore = fileStore;
    }

    public Optional<TestDurationPlan> planForCommit(Path projectPath, String commitHash) throws IOException {
        Optional<BetterGitConfiguration> configuration = configurationLoader.load(projectPath);
        if (configuration.isEmpty()
                || !configuration.orElseThrow().settings().testDurationTracking()) {
            return Optional.empty();
        }
        if (!commitHash.matches("[0-9a-f]{40}")) {
            throw new IOException("Commit hash must contain 40 lowercase hexadecimal characters.");
        }
        Path outputFile = projectPath.resolve(".bettergit/test-durations/" + commitHash + ".md");
        return Optional.of(new TestDurationPlan(projectPath, commitHash, outputFile));
    }

    public void track(TestDurationPlan plan)
            throws IOException, InterruptedException, TestSuiteFailedException {
        fileStore.ensureTestDurationsIgnored(plan.projectPath());
        BetterGitDirectories.child(plan.projectPath(), "test-durations");
        TestRun testRun;
        try (CommittedProjectSnapshot snapshot =
                CommittedProjectSnapshot.create(plan.projectPath(), plan.commitHash())) {
            testRun = testSuiteRunner.run(snapshot.directory());
        }
        AtomicFileWriter.write(plan.outputFile(), report(plan.commitHash(), testRun));
        if (!testRun.passed()) {
            throw new TestSuiteFailedException(plan.outputFile(), testRun.duration(), testRun.exitCode());
        }
    }

    public static String readableDuration(Duration duration) {
        return String.format(Locale.ROOT, "%.3f seconds", duration.toNanos() / 1_000_000_000.0);
    }

    private static String report(String commitHash, TestRun testRun) {
        String status = testRun.passed() ? "Passed" : "Failed";
        return """
                Tracks test-suite execution time for commit %s.

                ## Result

                %s (exit code %d).

                ## Duration

                %s

                ## Command

                `%s`
                """.formatted(
                commitHash, status, testRun.exitCode(), readableDuration(testRun.duration()), testRun.command());
    }
}
