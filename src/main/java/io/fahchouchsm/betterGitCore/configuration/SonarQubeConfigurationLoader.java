package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public final class SonarQubeConfigurationLoader {
    public static final String HOST_URL = "SONAR_HOST_URL";
    public static final String PROJECT_KEY = "SONAR_PROJECT_KEY";
    public static final String TOKEN = "SONAR_TOKEN";
    private static final Set<String> SUPPORTED_SETTINGS = Set.of(HOST_URL, PROJECT_KEY, TOKEN);
    private final ProjectEnvironmentFile environmentFile = new ProjectEnvironmentFile();

    public SonarQubeConfiguration load(
            Path projectPath, SonarQubeSettings settings, Map<String, String> environment) throws IOException {
        Map<String, String> fileSettings = environmentFile.read(projectPath, SUPPORTED_SETTINGS);
        return new SonarQubeConfiguration(
                preferred(HOST_URL, environment, fileSettings, settings.serverUrl()),
                preferred(PROJECT_KEY, environment, fileSettings, settings.projectKey()),
                preferred(TOKEN, environment, fileSettings, null),
                settings.trigger(), settings.branches(), settings.failurePolicy());
    }

    private static String preferred(
            String name, Map<String, String> environment, Map<String, String> fileSettings, String fallback) {
        if (environment.containsKey(name)) {
            return environment.get(name);
        }
        return fileSettings.getOrDefault(name, fallback);
    }
}
