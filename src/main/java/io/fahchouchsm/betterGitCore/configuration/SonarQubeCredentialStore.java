package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class SonarQubeCredentialStore {
    private static final List<String> SETTING_ORDER = List.of(SonarQubeConfigurationLoader.TOKEN);
    private final ProjectEnvironmentFile environmentFile = new ProjectEnvironmentFile();

    public void update(Path projectPath, String token) throws IOException {
        environmentFile.update(
                projectPath, SETTING_ORDER, Map.of(SonarQubeConfigurationLoader.TOKEN, token));
    }
}
