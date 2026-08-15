package io.fahchouchsm.betterGitCore.configuration;

import io.fahchouchsm.betterGitCore.commands.console.ConfirmationDefault;
import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AiSetupService {
    public static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    public static final String DEFAULT_GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    private final BetterGitFileStore fileStore;
    private final AiCredentialStore credentialStore;

    public AiSetupService(BetterGitFileStore fileStore, AiCredentialStore credentialStore) {
        this.fileStore = fileStore;
        this.credentialStore = credentialStore;
    }

    public AiConfiguration complete(
            Path projectPath, AiConfiguration current, ConsolePort console) throws IOException {
        if (current.isComplete()) {
            console.info("AI is already configured for model " + current.model() + ".");
            return current;
        }
        if (!console.confirm("Complete guided AI setup now?", ConfirmationDefault.YES)) {
            console.warning("AI setup deferred. Run 'bettergit ai setup' whenever you are ready.");
            return current;
        }
        return collectAndSave(projectPath, current, console);
    }

    private AiConfiguration collectAndSave(
            Path projectPath, AiConfiguration current, ConsolePort console) throws IOException {
        Map<String, String> localSettings = new LinkedHashMap<>();
        if (!current.hasApiKey()) {
            console.info("Get a Gemini API key: https://aistudio.google.com/app/apikey");
        }
        String apiKey = apiKey(current, console, localSettings);
        if (apiKey.isBlank()) {
            console.warning("AI setup deferred because no API key was entered.");
            return current;
        }
        String model = setting(console, "AI model", current.model(), DEFAULT_GEMINI_MODEL);
        String apiUrl = setting(console, "Gemini API URL", current.apiUrl(), DEFAULT_GEMINI_URL);
        AiConfiguration configured = new AiConfiguration(apiKey, model, apiUrl);
        if (!configured.isComplete()) {
            console.warning("AI setup was not saved because the key, model, or HTTP endpoint is incomplete.");
            return current;
        }
        save(projectPath, configured, localSettings);
        console.success("AI configured locally. The API key was masked and .env is ignored by Git.");
        return configured;
    }

    private void save(
            Path projectPath, AiConfiguration configured, Map<String, String> localSettings) throws IOException {
        String model = configured.model();
        String apiUrl = configured.apiUrl();
        localSettings.put(AiConfigurationLoader.API_MODEL, model);
        localSettings.put(AiConfigurationLoader.API_URL, apiUrl);
        fileStore.ensureEnvIgnored(projectPath);
        credentialStore.update(projectPath, localSettings);
    }

    private static String apiKey(
            AiConfiguration current, ConsolePort console, Map<String, String> localSettings) throws IOException {
        if (current.hasApiKey()) {
            return current.apiKey();
        }
        String enteredKey = console.readSecret("AI API key (input hidden): ").strip();
        if (!enteredKey.isBlank()) {
            localSettings.put(AiConfigurationLoader.API_KEY, enteredKey);
        }
        return enteredKey;
    }

    private static String setting(
            ConsolePort console, String label, String current, String fallback) throws IOException {
        if (current != null && !current.isBlank()) {
            return current;
        }
        String defaultSuffix = fallback == null ? ": " : " [" + fallback + "]: ";
        String entered = console.readLine(label + defaultSuffix).strip();
        return entered.isBlank() ? fallback : entered;
    }
}
