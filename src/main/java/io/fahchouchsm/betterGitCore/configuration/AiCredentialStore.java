package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AiCredentialStore {
    private static final List<String> SETTING_ORDER = List.of(
            AiConfigurationLoader.API_PROVIDER,
            AiConfigurationLoader.API_KEY,
            AiConfigurationLoader.API_MODEL,
            AiConfigurationLoader.API_URL);
    private static final Set<String> SUPPORTED_SETTINGS = Set.copyOf(SETTING_ORDER);

    public void update(Path projectPath, Map<String, String> newSettings) throws IOException {
        Map<String, String> validatedSettings = validated(newSettings);
        Path envFile = projectPath.resolve(".env");
        if (Files.isSymbolicLink(envFile)) {
            throw new IOException("Refusing to write AI credentials through a symbolic .env file.");
        }
        List<String> lines = existingLinesExcluding(envFile, validatedSettings.keySet());
        for (String setting : SETTING_ORDER) {
            if (validatedSettings.containsKey(setting)) {
                lines.add(setting + "=" + validatedSettings.get(setting));
            }
        }
        AtomicFileWriter.writeOwnerOnly(envFile, String.join(System.lineSeparator(), lines)
                + System.lineSeparator());
    }

    private static Map<String, String> validated(Map<String, String> newSettings) {
        Map<String, String> validated = new LinkedHashMap<>();
        for (Map.Entry<String, String> setting : newSettings.entrySet()) {
            if (!SUPPORTED_SETTINGS.contains(setting.getKey())) {
                throw new IllegalArgumentException("Unsupported AI setting: " + setting.getKey());
            }
            String settingValue = setting.getValue();
            if (settingValue == null || settingValue.isBlank()
                    || settingValue.contains("\n") || settingValue.contains("\r")) {
                throw new IllegalArgumentException(setting.getKey() + " must be one non-blank line.");
            }
            validated.put(setting.getKey(), settingValue.strip());
        }
        return validated;
    }

    private static List<String> existingLinesExcluding(Path envFile, Set<String> replacedSettings)
            throws IOException {
        if (!Files.isRegularFile(envFile)) {
            return new ArrayList<>();
        }
        return Files.readAllLines(envFile).stream()
                .filter(line -> !replacedSettings.contains(settingName(line)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static String settingName(String line) {
        int separator = line.indexOf('=');
        return separator <= 0 ? "" : line.substring(0, separator).trim();
    }
}
