package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectEnvironmentFile {
    public Map<String, String> read(Path projectPath, Set<String> supportedSettings) throws IOException {
        Path envFile = projectPath.resolve(".env");
        if (!Files.isRegularFile(envFile)) {
            return Map.of();
        }
        Map<String, String> settings = new HashMap<>();
        for (String line : Files.readAllLines(envFile)) {
            addSetting(settings, supportedSettings, line);
        }
        return Map.copyOf(settings);
    }

    public void update(
            Path projectPath, List<String> settingOrder, Map<String, String> replacements) throws IOException {
        validate(settingOrder, replacements);
        Path envFile = projectPath.resolve(".env");
        if (Files.isSymbolicLink(envFile)) {
            throw new IOException("Refusing to write credentials through a symbolic .env file.");
        }
        List<String> lines = existingLinesExcluding(envFile, replacements.keySet());
        for (String setting : settingOrder) {
            if (replacements.containsKey(setting)) {
                lines.add(setting + "=" + replacements.get(setting).strip());
            }
        }
        AtomicFileWriter.writeOwnerOnly(
                envFile, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }

    private static void validate(List<String> settingOrder, Map<String, String> replacements) {
        Set<String> supportedSettings = Set.copyOf(settingOrder);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            if (!supportedSettings.contains(replacement.getKey())) {
                throw new IllegalArgumentException("Unsupported environment setting: " + replacement.getKey());
            }
            String settingValue = replacement.getValue();
            if (settingValue == null || settingValue.isBlank()
                    || settingValue.contains("\n") || settingValue.contains("\r")) {
                throw new IllegalArgumentException(replacement.getKey() + " must be one non-blank line.");
            }
        }
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

    private static void addSetting(
            Map<String, String> settings, Set<String> supportedSettings, String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
            return;
        }
        int separator = trimmedLine.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String settingName = trimmedLine.substring(0, separator).trim();
        if (supportedSettings.contains(settingName)) {
            settings.put(settingName, unquote(trimmedLine.substring(separator + 1).trim()));
        }
    }

    private static String settingName(String line) {
        int separator = line.indexOf('=');
        return separator <= 0 ? "" : line.substring(0, separator).trim();
    }

    private static String unquote(String settingValue) {
        if (settingValue.length() < 2) {
            return settingValue;
        }
        char first = settingValue.charAt(0);
        char last = settingValue.charAt(settingValue.length() - 1);
        return (first == '"' && last == '"') || (first == '\'' && last == '\'')
                ? settingValue.substring(1, settingValue.length() - 1)
                : settingValue;
    }
}
