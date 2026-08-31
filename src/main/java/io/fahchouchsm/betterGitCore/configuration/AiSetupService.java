package io.fahchouchsm.betterGitCore.configuration;

import io.fahchouchsm.betterGitCore.commands.console.ConfirmationDefault;
import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiSetupService {
    private final BetterGitFileStore fileStore;
    private final AiCredentialStore credentialStore;
    private final AiModelSource modelSource;

    public AiSetupService(BetterGitFileStore fileStore, AiCredentialStore credentialStore) {
        this(fileStore, credentialStore, new AiModelCatalog());
    }

    public AiSetupService(
            BetterGitFileStore fileStore,
            AiCredentialStore credentialStore,
            AiModelSource modelSource) {
        this.fileStore = fileStore;
        this.credentialStore = credentialStore;
        this.modelSource = modelSource;
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
        return collectAndSave(projectPath, current, console, KeyEntryMode.REUSE_EXISTING);
    }

    public AiConfiguration configure(
            Path projectPath, AiConfiguration current, ConsolePort console) throws IOException {
        if (current.isComplete() && !console.confirm(
                "AI is configured for " + current.model() + ". Reconfigure it?",
                ConfirmationDefault.NO)) {
            return current;
        }
        return collectAndSave(projectPath, current, console, KeyEntryMode.PROMPT_FOR_REPLACEMENT);
    }

    private AiConfiguration collectAndSave(
            Path projectPath,
            AiConfiguration current,
            ConsolePort console,
            KeyEntryMode keyEntryMode) throws IOException {
        ServiceSelection selection = new ServiceSelection(selectedPreset(console, current), current);
        showKeyLocation(console, selection);
        String apiKey = apiKey(selection, console, keyEntryMode);
        if (apiKey.isBlank()) {
            console.warning("AI setup deferred because no API key was entered.");
            return current;
        }
        String apiUrl = apiUrl(console, selection);
        ModelSelection modelSelection = new ModelSelection(
                selection.preset(), selection.currentModel(), apiKey, apiUrl);
        String model = model(console, modelSelection);
        AiConfiguration configured = new AiConfiguration(
                selection.preset().provider(), apiKey, model, apiUrl);
        if (!configured.isComplete()) {
            console.warning("AI setup was not saved because the key, model, or HTTP endpoint is incomplete.");
            return current;
        }
        save(projectPath, configured, current);
        console.success("AI configured locally. The API key was masked and .env is ignored by Git.");
        return configured;
    }

    private void save(
            Path projectPath,
            AiConfiguration configured,
            AiConfiguration previous) throws IOException {
        Map<String, String> localSettings = new LinkedHashMap<>();
        localSettings.put(AiConfigurationLoader.API_PROVIDER, configured.provider().setting());
        localSettings.put(AiConfigurationLoader.API_MODEL, configured.model());
        localSettings.put(AiConfigurationLoader.API_URL, configured.apiUrl());
        if (!previous.hasApiKey() || !previous.apiKey().equals(configured.apiKey())) {
            localSettings.put(AiConfigurationLoader.API_KEY, configured.apiKey());
        }
        fileStore.ensureEnvIgnored(projectPath);
        credentialStore.update(projectPath, localSettings);
    }

    private static AiServicePreset selectedPreset(ConsolePort console, AiConfiguration current) throws IOException {
        int defaultChoice = current.provider() == null ? 0 : AiServicePreset.selectedIndex(current);
        int choice = console.chooseOne("Choose your AI service", AiServicePreset.labels(), defaultChoice);
        return AiServicePreset.values()[choice];
    }

    private static void showKeyLocation(ConsolePort console, ServiceSelection selection) {
        if (!selection.canReuseKey() && selection.preset().keyUrl() != null) {
            console.info("Create an API key: " + selection.preset().keyUrl());
        }
    }

    private static String apiKey(
            ServiceSelection selection,
            ConsolePort console,
            KeyEntryMode keyEntryMode) throws IOException {
        if (selection.canReuseKey() && keyEntryMode == KeyEntryMode.REUSE_EXISTING) {
            return selection.current().apiKey();
        }
        if (selection.canReuseKey()) {
            String replacement = console.readSecret(
                    "AI API key (input hidden; leave blank to keep the current key): ").strip();
            return replacement.isBlank() ? selection.current().apiKey() : replacement;
        }
        return console.readSecret("AI API key (input hidden): ").strip();
    }

    private static String apiUrl(ConsolePort console, ServiceSelection selection) throws IOException {
        if (selection.preset().apiUrl() != null) {
            return selection.preset().apiUrl();
        }
        return requiredSetting(
                console, "OpenAI-compatible chat completions URL", selection.currentUrl());
    }

    private String model(ConsolePort console, ModelSelection selection) throws IOException {
        URI modelsEndpoint = modelsEndpoint(selection.preset(), selection.apiUrl());
        if (modelsEndpoint == null || !console.confirm(
                "Discover available models automatically?", ConfirmationDefault.YES)) {
            return requiredSetting(console, "AI model", selection.currentModel());
        }
        List<String> models = discoverModels(
                console, selection.preset().provider(), modelsEndpoint, selection.apiKey());
        return models.isEmpty()
                ? requiredSetting(console, "AI model", selection.currentModel())
                : selectedModel(console, models, selection.currentModel());
    }

    private List<String> discoverModels(
            ConsolePort console, AiProvider provider, URI endpoint, String apiKey) {
        try {
            return modelSource.availableModels(provider, endpoint, apiKey);
        } catch (IOException exception) {
            console.warning("Model discovery failed; enter a model manually.");
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            console.warning("Model discovery was interrupted; enter a model manually.");
            return List.of();
        }
    }

    private static String selectedModel(ConsolePort console, List<String> models, String current)
            throws IOException {
        List<String> choices = new ArrayList<>(models);
        choices.add("Enter a model manually");
        int currentIndex = current == null ? -1 : models.indexOf(current);
        int selected = console.chooseOne(
                "Choose an AI model", choices, currentIndex < 0 ? 0 : currentIndex);
        return selected == models.size()
                ? requiredSetting(console, "AI model", current)
                : models.get(selected);
    }

    private static URI modelsEndpoint(AiServicePreset preset, String apiUrl) {
        if (preset.modelsEndpoint() != null) {
            return preset.modelsEndpoint();
        }
        if (apiUrl == null || !apiUrl.endsWith("/chat/completions")) {
            return null;
        }
        try {
            URI endpoint = URI.create(
                    apiUrl.substring(0, apiUrl.length() - "/chat/completions".length()) + "/models");
            return endpoint.getHost() == null ? null : endpoint;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String requiredSetting(ConsolePort console, String label, String current) throws IOException {
        String suffix = current == null || current.isBlank() ? ": " : " [" + current + "]: ";
        String entered = console.readLine(label + suffix).strip();
        return entered.isBlank() ? current : entered;
    }

    private record ServiceSelection(AiServicePreset preset, AiConfiguration current) {
        private boolean matchesCurrent() {
            return preset.matches(current);
        }

        private boolean canReuseKey() {
            return matchesCurrent() && current.hasApiKey();
        }

        private String currentModel() {
            return matchesCurrent() ? current.model() : null;
        }

        private String currentUrl() {
            return matchesCurrent() ? current.apiUrl() : null;
        }
    }

    private record ModelSelection(
            AiServicePreset preset, String currentModel, String apiKey, String apiUrl) {
    }

    private enum KeyEntryMode {
        REUSE_EXISTING,
        PROMPT_FOR_REPLACEMENT
    }
}
