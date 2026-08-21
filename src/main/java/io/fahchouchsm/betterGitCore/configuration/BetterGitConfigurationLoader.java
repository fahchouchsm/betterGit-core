package io.fahchouchsm.betterGitCore.configuration;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class BetterGitConfigurationLoader {
    private static final Gson JSON = new Gson();

    public Optional<BetterGitConfiguration> load(Path projectPath) throws IOException {
        Path configurationFile = projectPath.resolve(".bettergit/config.json");
        if (!Files.isRegularFile(configurationFile)) {
            return Optional.empty();
        }
        try {
            BetterGitConfiguration configuration = JSON.fromJson(
                    Files.readString(configurationFile), BetterGitConfiguration.class);
            if (configuration == null) {
                throw new IOException(".bettergit/config.json must contain a JSON object.");
            }
            return Optional.of(configuration);
        } catch (JsonParseException exception) {
            throw new IOException("Could not read .bettergit/config.json.", exception);
        }
    }
}
