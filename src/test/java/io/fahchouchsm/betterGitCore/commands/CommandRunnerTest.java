package io.fahchouchsm.betterGitCore.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRunnerTest {
    @TempDir
    Path projectPath;

    @Test
    void noCommandShowsRootHelpAndSucceeds() {
        RecordingConsole console = new RecordingConsole();

        int exitCode = execute(console, new TestRepository(true));

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(console.output().contains("BetterGit - Git workflows enhanced for Java projects."));
        assertTrue(console.output().contains("Usage:"));
        assertTrue(console.output().contains("init"));
        assertTrue(console.output().contains("help"));
    }

    @Test
    void rootHelpAndVersionUseStandardOptions() {
        RecordingConsole helpConsole = new RecordingConsole();
        RecordingConsole versionConsole = new RecordingConsole();

        int helpExitCode = execute(helpConsole, new TestRepository(true), "--help");
        int versionExitCode = execute(versionConsole, new TestRepository(true), "--version");

        assertEquals(CommandLine.ExitCode.OK, helpExitCode);
        assertTrue(helpConsole.output().contains("--no-color"));
        assertTrue(helpConsole.output().contains("--verbose"));
        assertEquals(CommandLine.ExitCode.OK, versionExitCode);
        assertTrue(versionConsole.output().contains("BetterGit development"));
    }

    @Test
    void unknownCommandReturnsUsageExitCodeAndGuidance() {
        RecordingConsole console = new RecordingConsole();

        int exitCode = execute(console, new TestRepository(true), "unknown");

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        assertTrue(console.errors().contains("Unmatched argument"));
        assertTrue(console.errors().contains("bettergit --help"));
    }

    @Test
    void initHelpDoesNotRunInitialization() {
        RecordingConsole console = new RecordingConsole();
        TestRepository repository = new TestRepository(false);

        int exitCode = execute(console, repository, "init", "--help");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(console.output().contains("Configure BetterGit for the current project."));
        assertTrue(console.output().contains("--yes"));
        assertEquals(0, repository.detectionCount);
        assertEquals(0, repository.initializationCount);
    }

    @Test
    void yesRunsNonInteractivelyWithSafeDefaults() throws Exception {
        java.nio.file.Files.writeString(projectPath.resolve("pom.xml"), "<project/>");
        RecordingConsole console = new RecordingConsole("yes", "yes", "yes");
        TestRepository repository = new TestRepository(false);

        int exitCode = execute(console, repository, "init", "--yes");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(console.prompts().isEmpty());
        assertEquals(1, repository.initializationCount);
        assertTrue(console.output().contains("BetterGit initialization complete."));
        assertTrue(console.output().contains("Class diagrams: disabled"));
    }

    @Test
    void inheritedNoColorAndVerboseOptionsWorkAfterSubcommand() {
        RecordingConsole console = new RecordingConsole();

        int exitCode = execute(
                console, new TestRepository(true), "init", "--yes", "--no-color", "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode);
        assertTrue(console.noColor());
        assertTrue(console.output().contains("Diagnostic:"));
        assertFalse(console.output().contains("\u001B"));
    }

    @Test
    void stackTracesArePrintedOnlyInVerboseMode() {
        String secret = "secret-api-key";
        RepositoryAccess failingRepository = new RepositoryAccess() {
            @Override
            public boolean isInsideRepository(Path projectPath) {
                throw new IllegalStateException("repository check failed with " + secret);
            }

            @Override
            public void initialize(Path projectPath) {
            }
        };
        RecordingConsole normalConsole = new RecordingConsole();
        RecordingConsole verboseConsole = new RecordingConsole();

        int normalExitCode = execute(normalConsole, failingRepository, "init", "--yes");
        int verboseExitCode = execute(verboseConsole, failingRepository, "init", "--yes", "--verbose");

        assertEquals(CommandLine.ExitCode.SOFTWARE, normalExitCode);
        assertFalse(normalConsole.errors().contains("at io.fahchouchsm"));
        assertFalse(normalConsole.errors().contains(secret));
        assertEquals(CommandLine.ExitCode.SOFTWARE, verboseExitCode);
        assertTrue(verboseConsole.errors().contains("IllegalStateException"));
        assertTrue(verboseConsole.errors().contains("at io.fahchouchsm"));
        assertFalse(verboseConsole.errors().contains(secret));
    }

    @Test
    void runtimeDiagnosticRepresentationRedactsEnvironment() {
        String secret = "runtime-secret";
        CommandRuntime runtime = new CommandRuntime(
                projectPath,
                new RecordingConsole(),
                new TestRepository(true),
                Map.of("AI_API_KEY", secret),
                Clock.systemUTC(),
                (configuration, prompt) -> "# Generated");

        assertFalse(runtime.toString().contains(secret));
    }

    private int execute(RecordingConsole console, RepositoryAccess repository, String... arguments) {
        return CommandRunner.execute(arguments, new CommandRuntime(
                projectPath,
                console,
                repository,
                Map.of(),
                Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC),
                (configuration, prompt) -> "# Generated"));
    }

    private static final class TestRepository implements RepositoryAccess {
        private final boolean exists;
        private int detectionCount;
        private int initializationCount;

        private TestRepository(boolean exists) {
            this.exists = exists;
        }

        @Override
        public boolean isInsideRepository(Path projectPath) {
            detectionCount++;
            return exists;
        }

        @Override
        public void initialize(Path projectPath) {
            initializationCount++;
        }
    }
}
