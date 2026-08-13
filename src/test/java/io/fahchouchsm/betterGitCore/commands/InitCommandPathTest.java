package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.testsupport.TestConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

        int exitCode = BetterGitCli.run(new String[]{"init"}, projectPath, new DefaultAnswerConsole());

        assertEquals(InitCommand.SUCCESS, exitCode);
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/config.json")));
        assertTrue(Files.isRegularFile(projectPath.resolve(".bettergit/general.md")));
    }

    private static boolean livePathTestIsEnabled(Map<String, String> configuration, String configuredPath) {
        return "true".equalsIgnoreCase(configuration.get("RUN_LIVE_TESTS"))
                && configuredPath != null
                && !configuredPath.isBlank();
    }

    private static final class DefaultAnswerConsole implements CommandConsole {
        @Override
        public String readLine(String prompt) {
            System.out.println(prompt + "[default: no]");
            return "";
        }

        @Override
        public void println(String message) {
            System.out.println(message);
        }
    }
}
