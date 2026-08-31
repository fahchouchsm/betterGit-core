package io.fahchouchsm.betterGitCore.configuration;

import io.fahchouchsm.betterGitCore.commands.console.SystemConsoleAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSetupServiceTest {
    @TempDir
    Path projectPath;

    @Test
    void discoversAndPersistsASelectedModel() throws Exception {
        ModelRequest[] captured = new ModelRequest[1];
        AiModelSource models = (provider, endpoint, apiKey) -> {
            captured[0] = new ModelRequest(provider, endpoint, apiKey);
            return List.of("model-alpha", "model-beta");
        };
        AiSetupService service = service(models);

        AiConfiguration configured = service.configure(
                projectPath,
                new AiConfiguration((AiProvider) null, null, null, null),
                console("1\nopenai-key\ny\n2\n"));

        assertEquals(AiProvider.OPENAI_COMPATIBLE, configured.provider());
        assertEquals("model-beta", configured.model());
        assertEquals(new ModelRequest(
                AiProvider.OPENAI_COMPATIBLE,
                URI.create("https://api.openai.com/v1/models"),
                "openai-key"), captured[0]);
        String env = Files.readString(projectPath.resolve(".env"));
        assertTrue(env.contains("AI_API_PROVIDER=openai-compatible"));
        assertTrue(env.contains("AI_API_KEY=openai-key"));
        assertFalse(env.contains("null"));
    }

    @Test
    void switchingServicesRequiresAndStoresANewKeyAndModel() throws Exception {
        AiConfiguration current = new AiConfiguration(
                AiProvider.OPENAI_COMPATIBLE,
                "old-openai-key",
                "old-openai-model",
                "https://api.openai.com/v1/chat/completions");

        AiConfiguration configured = service((provider, endpoint, apiKey) -> List.of()).configure(
                projectPath,
                current,
                console("y\n2\nnew-gemini-key\nn\ngemini-2.5-flash\n"));

        assertEquals(AiProvider.GEMINI, configured.provider());
        assertEquals("new-gemini-key", configured.apiKey());
        assertEquals("gemini-2.5-flash", configured.model());
        String env = Files.readString(projectPath.resolve(".env"));
        assertTrue(env.contains("AI_API_KEY=new-gemini-key"));
        assertFalse(env.contains("old-openai-key"));
        assertFalse(env.contains("old-openai-model"));
    }

    @Test
    void reconfiguringTheSameServicePromptsForAndStoresAReplacementKey() throws Exception {
        AiConfiguration current = new AiConfiguration(
                AiProvider.OPENAI_COMPATIBLE,
                "old-openai-key",
                "openai-model",
                "https://api.openai.com/v1/chat/completions");

        AiConfiguration configured = service((provider, endpoint, apiKey) -> List.of()).configure(
                projectPath,
                current,
                console("y\n1\nreplacement-key\nn\n\n"));

        assertEquals("replacement-key", configured.apiKey());
        assertEquals("openai-model", configured.model());
        String env = Files.readString(projectPath.resolve(".env"));
        assertTrue(env.contains("AI_API_KEY=replacement-key"));
        assertFalse(env.contains("old-openai-key"));
    }

    @Test
    void rejectsAnInvalidCustomEndpointWithoutWritingCredentials() throws Exception {
        AiConfiguration current = new AiConfiguration((AiProvider) null, null, null, null);

        AiConfiguration configured = service((provider, endpoint, apiKey) -> List.of()).configure(
                projectPath,
                current,
                console("6\ncustom-key\nnot-a-url\ncustom-model\n"));

        assertFalse(configured.isComplete());
        assertFalse(Files.exists(projectPath.resolve(".env")));
    }

    private AiSetupService service(AiModelSource modelSource) {
        return new AiSetupService(new BetterGitFileStore(), new AiCredentialStore(), modelSource);
    }

    private static SystemConsoleAdapter console(String input) {
        return new SystemConsoleAdapter(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintWriter(new StringWriter(), true),
                new PrintWriter(new StringWriter(), true));
    }

    private record ModelRequest(AiProvider provider, URI endpoint, String apiKey) {
    }
}
