package io.fahchouchsm.betterGitCore.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Loads opt-in integration-test settings from the ignored .env.test file. */
public final class TestConfiguration {
    private TestConfiguration() {
    }

    public static Map<String, String> read() throws IOException {
        Path configurationFile = Path.of(".env.test");
        if (!Files.isRegularFile(configurationFile)) {
            return Map.of();
        }
        Map<String, String> settings = new HashMap<>();
        for (String line : Files.readAllLines(configurationFile)) {
            addSetting(settings, line);
        }
        return settings;
    }

    private static void addSetting(Map<String, String> settings, String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return;
        }
        int separator = trimmedLine.indexOf('=');
        if (separator > 0) {
            settings.put(trimmedLine.substring(0, separator).trim(), trimmedLine.substring(separator + 1).trim());
        }
    }
}
