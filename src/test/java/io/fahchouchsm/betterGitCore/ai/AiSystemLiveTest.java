package io.fahchouchsm.betterGitCore.ai;

import io.fahchouchsm.betterGitCore.testsupport.TestConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Opt-in integration test for a real Gemini API.
 * It is skipped unless .env.test contains all required values.
 */
class AiSystemLiveTest {
    @Test
    void sendsInputAndPrintsAiOutput() throws Exception {
        Map<String, String> configuration = TestConfiguration.read();
        Assumptions.assumeTrue(liveTestsAreEnabled(configuration),
                "Set RUN_LIVE_TESTS=true and configure AI_API_KEY, AI_API_MODEL, and AI_API_URL in .env.test.");
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
        return java.util.stream.Stream.of("AI_API_KEY", "AI_API_MODEL", "AI_API_URL", "AI_INPUT")
                .map(configuration::get)
                .allMatch(value -> value != null && !value.isBlank());
    }
}
