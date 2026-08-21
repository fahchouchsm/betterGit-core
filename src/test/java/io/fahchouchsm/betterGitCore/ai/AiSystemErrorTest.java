package io.fahchouchsm.betterGitCore.ai;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSystemErrorTest {
    @Test
    void rejectsMissingConfiguration() {
        assertThrows(AiConfigurationException.class, () -> AiSystem.fromConfiguration(Map.of()));
    }

    @Test
    void rejectsInvalidApiUrl() {
        Map<String, String> configuration = Map.of(
                "AI_API_KEY", "test-key", "AI_API_MODEL", "test-model", "AI_API_URL", "not a URL");

        assertThrows(AiConfigurationException.class, () -> AiSystem.fromConfiguration(configuration));
    }

    @Test
    void rejectsUnsupportedEndpointProtocol() {
        assertThrows(AiConfigurationException.class, () -> new AiSystem("test-key", URI.create("ftp://example.test")));
    }

    @Test
    void rejectsBlankInputBeforeMakingARequest() {
        AiSystem aiSystem = new AiSystem("test-key", URI.create("https://example.test"));

        assertThrows(AiInputException.class, () -> aiSystem.generate(" "));
    }

    @Test
    void rejectsEmptyOrMalformedAiOutput() {
        assertThrows(AiResponseException.class, () -> AiSystem.extractOutputText("{}"));
        assertThrows(AiResponseException.class, () -> AiSystem.extractOutputText("{\"text\":\"unterminated"));
    }
}
