package io.fahchouchsm.betterGitCore.ai;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSystemTest {
    @Test
    void extractsAndUnescapesOutputText() throws Exception {
        String response = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"First line\\nSecond line\"}]}}]}";
        assertEquals("First line\nSecond line", AiSystem.extractOutputText(response));
    }

    @Test
    void rejectsBlankKey() {
        AiConfigurationException exception = assertThrows(AiConfigurationException.class,
                () -> new AiSystem(" ", URI.create("https://example.test")));

        assertEquals("apiKey must not be blank", exception.getMessage());
    }

    @Test
    void rejectsResponsesWithoutCandidateParts() {
        assertThrows(AiResponseException.class,
                () -> AiSystem.extractOutputText("{\"text\":\"not generated content\"}"));
    }
}
