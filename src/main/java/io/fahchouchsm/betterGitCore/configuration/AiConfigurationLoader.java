package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Loads supported AI settings with environment variables taking precedence over project .env values. */
public final class AiConfigurationLoader {
    public static final String API_KEY = "AI_API_KEY";
    public static final String API_MODEL = "AI_API_MODEL";
    public static final String API_URL = "AI_API_URL";
    public static final String API_PROVIDER = "AI_API_PROVIDER";
    private static final Set<String> SUPPORTED_SETTINGS = Set.of(
            API_PROVIDER, API_KEY, API_MODEL, API_URL);
    private final ProjectEnvironmentFile environmentFile = new ProjectEnvironmentFile();

    public AiConfiguration load(Path projectPath, Map<String, String> environment) throws IOException {
        return load(projectPath, environment, null);
    }

    public AiConfiguration load(
            Path projectPath, Map<String, String> environment, String configuredModel) throws IOException {
        Map<String, String> fileSettings = environmentFile.read(projectPath, SUPPORTED_SETTINGS);
        String apiUrl = preferredValue(API_URL, environment, fileSettings);
        return new AiConfiguration(
                AiProvider.configured(preferredValue(API_PROVIDER, environment, fileSettings), apiUrl),
                preferredValue(API_KEY, environment, fileSettings),
                preferredModel(environment, fileSettings, configuredModel),
                apiUrl);
    }

    private static String preferredModel(
            Map<String, String> environment, Map<String, String> fileSettings, String configuredModel) {
        String configuredValue = preferredValue(API_MODEL, environment, fileSettings);
        return configuredValue == null || configuredValue.isBlank() ? configuredModel : configuredValue;
    }

    private static String preferredValue(
            String settingName, Map<String, String> environment, Map<String, String> fileSettings) {
        return environment.containsKey(settingName) ? environment.get(settingName) : fileSettings.get(settingName);
    }

}
