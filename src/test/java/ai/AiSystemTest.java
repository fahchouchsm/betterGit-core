package ai;

import io.fahchouchsm.betterGitCore.ai.AiSystem;
import junit.framework.TestCase;

public class AiSystemTest extends TestCase {
    public void testExtractsAndUnescapesOutputText() throws Exception {
        String response = "{\"output\":[{\"type\":\"message\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":\"First line\\nSecond line\"}]}]}";
        assertEquals("First line\nSecond line", AiSystem.extractOutputText(response));
    }

    public void testRejectsBlankKey() {
        try {
            new AiSystem(" ");
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("apiKey must not be blank", expected.getMessage());
        }
    }
}
