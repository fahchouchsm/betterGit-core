package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.testsupport.TestConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import io.fahchouchsm.betterGitCore.JGitManager.JGitManager;
import io.fahchouchsm.betterGitCore.commands.console.SystemConsoleAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in manual test that runs bettergit init against a configured project path. */
class InitCommandPathTest {
    @Test
    void initializesConfiguredProjectPath() throws Exception {
        Map<String, String> configuration = TestConfiguration.read();
        String configuredPath = configuration.get("BETTERGIT_INIT_PROJECT_PATH");
        Assumptions.assumeTrue(livePathTestIsEnabled(configuration, configuredPath),
                "Set RUN_LIVE_TESTS=true and BETTERGIT_INIT_PROJECT_PATH in .env.test.");
        Path projectPath = Path.of(configuredPath).toAbsolutePath().normalize();

        SystemConsoleAdapter console = new SystemConsoleAdapter(
                new ByteArrayInputStream(new byte[0]),
                new PrintWriter(System.out, true),
                new PrintWriter(System.err, true));
        int exitCode = CommandRunner.execute(new String[]{"init", "--yes"}, new CommandRuntime(
                projectPath,
                console,
                new JGitRepositoryAccess(new JGitManager()),
                Map.of(),
                Clock.systemUTC(),
                (aiConfiguration, prompt) -> "# Generated"));

        assertEquals(InitCommand.SUCCESS, exitCode);
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/config.json")));
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/general.md")));
    }

    private static boolean livePathTestIsEnabled(Map<String, String> configuration, String configuredPath) {
        return "true".equalsIgnoreCase(configuration.get("RUN_LIVE_TESTS"))
                && configuredPath != null
                && !configuredPath.isBlank();
    }
}
