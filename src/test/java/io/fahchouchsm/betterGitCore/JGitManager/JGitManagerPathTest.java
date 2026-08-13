package io.fahchouchsm.betterGitCore.JGitManager;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Read-only manual test for checking the repository path configured in .env.test. */
class JGitManagerPathTest {
    @Test
    void printsChangesForConfiguredRepositoryPath() throws IOException {
        Map<String, String> configuration = readTestConfiguration();
        String configuredPath = configuration.get("GIT_REPOSITORY_PATH");
        Assumptions.assumeTrue("true".equalsIgnoreCase(configuration.get("RUN_LIVE_TESTS"))
                        && configuredPath != null && !configuredPath.isBlank(),
                "Set RUN_LIVE_TESTS=true and GIT_REPOSITORY_PATH in .env.test to run this test.");

        GitChanges changes = new JGitManager().getChangesBeforeCommit(Path.of(configuredPath));

        System.out.println("Clean: " + changes.isClean());
        System.out.println("Staged new: " + changes.added());
        System.out.println("Staged modified: " + changes.changed());
        System.out.println("Staged removed: " + changes.removed());
        System.out.println("Unstaged modified: " + changes.modified());
        System.out.println("Missing: " + changes.missing());
        System.out.println("Untracked: " + changes.untracked());
        System.out.println("Conflicting: " + changes.conflicting());
        assertNotNull(changes);
    }

    private Map<String, String> readTestConfiguration() throws IOException {
        Path configurationFile = Path.of(".env.test");
        if (!Files.isRegularFile(configurationFile)) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(configurationFile)) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }
            int separator = trimmedLine.indexOf('=');
            if (separator > 0) {
                values.put(trimmedLine.substring(0, separator).trim(), trimmedLine.substring(separator + 1).trim());
            }
        }
        return values;
    }
}
