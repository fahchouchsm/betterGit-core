package io.fahchouchsm.betterGitCore.api;

import io.fahchouchsm.betterGitCore.configuration.AiProvider;

public record AiSettingsRequest(
        AiProvider provider,
        String model,
        String apiUrl,
        String apiKey,
        boolean commitReports,
        boolean memory) {

    public AiSettingsRequest {
        if (provider == null) {
            throw new IllegalArgumentException("An AI provider is required.");
        }
        model = requiredLine(model, "AI model");
        apiUrl = requiredLine(apiUrl, "AI API URL");
        apiKey = apiKey == null ? "" : apiKey.strip();
        if (apiKey.contains("\r") || apiKey.contains("\n")) {
            throw new IllegalArgumentException("AI API key must be one line.");
        }
    }

    private static String requiredLine(String text, String label) {
        if (text == null || text.isBlank() || text.contains("\r") || text.contains("\n")) {
            throw new IllegalArgumentException(label + " must be one non-blank line.");
        }
        return text.strip();
    }
}
