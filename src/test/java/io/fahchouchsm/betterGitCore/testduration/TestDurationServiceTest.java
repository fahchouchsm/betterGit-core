package io.fahchouchsm.betterGitCore.testduration;

import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDurationServiceTest {
    @TempDir
    Path projectPath;

    @Test
    void tracksTheExactCommittedProjectAndSavesItsDuration() throws Exception {
        String commitHash = committedProject();
        configureTrackingEnabled();
        Files.writeString(projectPath.resolve("Marker.java"), "working tree\n");
        Path[] measuredProject = new Path[1];
        TestDurationService service = service(committedProject -> {
            measuredProject[0] = committedProject;
            assertEquals("committed\n", Files.readString(committedProject.resolve("Marker.java")));
            return new TestRun("mvn test", Duration.ofMillis(1250), 0);
        });

        TestDurationPlan plan = service.planForCommit(projectPath, commitHash).orElseThrow();
        service.track(plan);

        String report = Files.readString(plan.outputFile());
        assertTrue(report.contains("Passed (exit code 0)"));
        assertTrue(report.contains("1.250 seconds"));
        assertTrue(report.contains("`mvn test`"));
        assertFalse(Files.exists(measuredProject[0]));
        assertTrue(Files.readString(projectPath.resolve(".gitignore"))
                .contains(".bettergit/test-durations/"));
    }

    @Test
    void preservesTheTimingReportWhenTestsFail() throws Exception {
        String commitHash = committedProject();
        configureTrackingEnabled();
        TestDurationService service = service(committedProject ->
                new TestRun("mvn test", Duration.ofMillis(500), 1));
        TestDurationPlan plan = service.planForCommit(projectPath, commitHash).orElseThrow();

        TestSuiteFailedException failure = assertThrows(
                TestSuiteFailedException.class, () -> service.track(plan));

        assertEquals(plan.outputFile(), failure.reportPath());
        assertEquals(Duration.ofMillis(500), failure.duration());
        assertTrue(Files.readString(plan.outputFile()).contains("Failed (exit code 1)"));
    }

    @Test
    void disabledTrackingDoesNotPlanOrRunTests() throws Exception {
        configureTrackingDisabled();
        TestDurationService service = service(committedProject -> {
            throw new AssertionError("disabled test-duration tracker was invoked");
        });

        Optional<TestDurationPlan> plan = service.planForCommit(
                projectPath, "1234567890123456789012345678901234567890");

        assertTrue(plan.isEmpty());
        assertFalse(Files.exists(projectPath.resolve(".bettergit/test-durations")));
    }

    @Test
    void runsFromTheConfiguredModuleInsideARepository() throws Exception {
        Path module = Files.createDirectories(projectPath.resolve("module"));
        Files.writeString(module.resolve("pom.xml"), "<project/>\n");
        String commitHash;
        try (Git git = Git.init().setDirectory(projectPath.toFile()).call()) {
            git.add().addFilepattern("module/pom.xml").call();
            commitHash = git.commit()
                    .setAuthor("BetterGit Test", "test@bettergit.local")
                    .setCommitter("BetterGit Test", "test@bettergit.local")
                    .setMessage("module fixture").call().name();
        }
        configure(module, new FeatureSettings(false, true, false));
        TestDurationService service = service(committedProject -> {
            assertTrue(Files.isRegularFile(committedProject.resolve("pom.xml")));
            assertFalse(Files.exists(committedProject.resolve("module")));
            return new TestRun("mvn test", Duration.ofMillis(250), 0);
        });

        TestDurationPlan plan = service.planForCommit(module, commitHash).orElseThrow();
        service.track(plan);

        assertTrue(Files.isRegularFile(plan.outputFile()));
    }

    private TestDurationService service(TestSuiteRunner runner) {
        return new TestDurationService(
                runner, new BetterGitConfigurationLoader(), new BetterGitFileStore());
    }

    private void configureTrackingEnabled() throws Exception {
        configure(new FeatureSettings(false, true, false));
    }

    private void configureTrackingDisabled() throws Exception {
        configure(FeatureSettings.disabled());
    }

    private void configure(FeatureSettings features) throws Exception {
        configure(projectPath, features);
    }

    private void configure(Path configuredProject, FeatureSettings features) throws Exception {
        BetterGitConfiguration configuration = new BetterGitConfiguration(
                2, "2026-08-21T12:00:00Z", configuredProject.toString(), true, true,
                features, false, AiCommitSettings.disabled(null));
        new BetterGitFileStore().writeInitialization(configuredProject, configuration, "# Test\n");
    }

    private String committedProject() throws Exception {
        Files.writeString(projectPath.resolve("pom.xml"), "<project/>\n");
        Files.writeString(projectPath.resolve("Marker.java"), "committed\n");
        try (Git git = Git.init().setDirectory(projectPath.toFile()).call()) {
            git.add().addFilepattern("pom.xml").addFilepattern("Marker.java").call();
            return git.commit()
                    .setAuthor("BetterGit Test", "test@bettergit.local")
                    .setCommitter("BetterGit Test", "test@bettergit.local")
                    .setMessage("fixture")
                    .call()
                    .name();
        }
    }
}
