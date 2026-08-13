package io.fahchouchsm.betterGitCore.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiConfigurationLoaderTest {
    private final AiConfigurationLoader loader = new AiConfigurationLoader();

    @TempDir
    Path projectPath;

    @Test
    void loadsApiSettingsFromEnvironment() throws Exception {
        AiConfiguration configuration = loader.load(projectPath, Map.of(
                "AI_API_KEY", "environment-key",
                "AI_API_MODEL", "environment-model",
                "AI_API_URL", "https://environment.example/api"));

        assertEquals("environment-key", configuration.apiKey());
        assertEquals("environment-model", configuration.model());
        assertEquals("https://environment.example/api", configuration.apiUrl());
    }

    @Test
    void loadsApiSettingsFromProjectEnvFile() throws Exception {
        Files.writeString(projectPath.resolve(".env"), """
                AI_API_KEY="file-key"
                AI_API_MODEL=file-model
                AI_API_URL='https://file.example/api'
                """);

        AiConfiguration configuration = loader.load(projectPath, Map.of());

        assertEquals("file-key", configuration.apiKey());
        assertEquals("file-model", configuration.model());
        assertEquals("https://file.example/api", configuration.apiUrl());
    }

    @Test
    void environmentSettingsOverrideProjectEnvFile() throws Exception {
        Files.writeString(projectPath.resolve(".env"), """
                AI_API_KEY=file-key
                AI_API_MODEL=file-model
                AI_API_URL=https://file.example/api
                """);

        AiConfiguration configuration = loader.load(projectPath, Map.of(
                "AI_API_KEY", "environment-key",
                "AI_API_MODEL", "environment-model",
                "AI_API_URL", "https://environment.example/api"));

        assertEquals("environment-key", configuration.apiKey());
        assertEquals("environment-model", configuration.model());
        assertEquals("https://environment.example/api", configuration.apiUrl());
    }
}
