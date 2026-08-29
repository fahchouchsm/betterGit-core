package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class AiCredentialStore {
    private static final List<String> SETTING_ORDER = List.of(
            AiConfigurationLoader.API_PROVIDER,
            AiConfigurationLoader.API_KEY,
            AiConfigurationLoader.API_MODEL,
            AiConfigurationLoader.API_URL);
    private final ProjectEnvironmentFile environmentFile = new ProjectEnvironmentFile();

    public void update(Path projectPath, Map<String, String> newSettings) throws IOException {
        environmentFile.update(projectPath, SETTING_ORDER, newSettings);
    }
}
