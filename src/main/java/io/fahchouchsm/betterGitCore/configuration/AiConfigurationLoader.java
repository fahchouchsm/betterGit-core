package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Loads supported AI settings with environment variables taking precedence over project .env values. */
public final class AiConfigurationLoader {
    static final String API_KEY = "AI_API_KEY";
    static final String API_MODEL = "AI_API_MODEL";
    static final String API_URL = "AI_API_URL";

    public AiConfiguration load(Path projectPath, Map<String, String> environment) throws IOException {
        return load(projectPath, environment, null);
    }

    public AiConfiguration load(
            Path projectPath, Map<String, String> environment, String configuredModel) throws IOException {
        Map<String, String> fileSettings = readEnvFile(projectPath.resolve(".env"));
        return new AiConfiguration(
                preferredValue(API_KEY, environment, fileSettings),
                preferredModel(environment, fileSettings, configuredModel),
                preferredValue(API_URL, environment, fileSettings));
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

    private static Map<String, String> readEnvFile(Path envFile) throws IOException {
        if (!Files.isRegularFile(envFile)) {
            return Map.of();
        }
        Map<String, String> settings = new HashMap<>();
        for (String line : Files.readAllLines(envFile)) {
            addSupportedSetting(settings, line);
        }
        return settings;
    }

    private static void addSupportedSetting(Map<String, String> settings, String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return;
        }
        int separator = trimmedLine.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String settingName = trimmedLine.substring(0, separator).trim();
        if (API_KEY.equals(settingName) || API_MODEL.equals(settingName) || API_URL.equals(settingName)) {
            settings.put(settingName, unquote(trimmedLine.substring(separator + 1).trim()));
        }
    }

    private static String unquote(String settingValue) {
        if (settingValue.length() >= 2) {
            char first = settingValue.charAt(0);
            char last = settingValue.charAt(settingValue.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return settingValue.substring(1, settingValue.length() - 1);
            }
        }
        return settingValue;
    }
}
