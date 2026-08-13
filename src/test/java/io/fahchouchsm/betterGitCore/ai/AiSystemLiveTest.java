package io.fahchouchsm.betterGitCore.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Opt-in integration test for a real Gemini API.
 * It is skipped unless .env.test contains all required values.
 */
class AiSystemLiveTest {
    @Test
    void sendsInputAndPrintsAiOutput() throws Exception {
        Map<String, String> configuration = readTestConfiguration();
        Assumptions.assumeTrue(liveTestsAreEnabled(configuration),
                "Set RUN_LIVE_TESTS=true and configure the AI values in .env.test to run this test.");
        String input = configuration.get("AI_INPUT");

        AiSystem aiSystem = AiSystem.fromConfiguration(configuration);
        String output = aiSystem.generate(input);

        System.out.println("AI output:\n" + output);
        assertFalse(output.isBlank());
    }

    private boolean liveTestsAreEnabled(Map<String, String> configuration) {
        if (!"true".equalsIgnoreCase(configuration.get("RUN_LIVE_TESTS"))) {
            return false;
        }
        return java.util.stream.Stream.of("AI_API_KEY", "AI_MODEL", "AI_API_URL_TEMPLATE", "AI_INPUT")
                .map(configuration::get)
                .allMatch(value -> value != null && !value.isBlank());
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
