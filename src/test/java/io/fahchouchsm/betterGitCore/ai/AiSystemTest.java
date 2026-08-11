package io.fahchouchsm.betterGitCore.ai;

import junit.framework.TestCase;

import java.net.URI;

public class AiSystemTest extends TestCase {
    public void testExtractsAndUnescapesOutputText() throws Exception {
        String response = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"First line\\nSecond line\"}]}}]}";
        assertEquals("First line\nSecond line", AiSystem.extractOutputText(response));
    }

    public void testRejectsBlankKey() {
        try {
            new AiSystem(" ", URI.create("https://example.test"));
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("apiKey must not be blank", expected.getMessage());
        }
    }
}
