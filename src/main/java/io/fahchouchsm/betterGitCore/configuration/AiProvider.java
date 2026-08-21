package io.fahchouchsm.betterGitCore.configuration;

import io.fahchouchsm.betterGitCore.ai.AiConfigurationException;

import java.util.Locale;

public enum AiProvider {
    OPENAI_COMPATIBLE("openai-compatible"),
    GEMINI("gemini"),
    ANTHROPIC("anthropic");

    private final String setting;

    AiProvider(String setting) {
        this.setting = setting;
    }

    public String setting() {
        return setting;
    }

    public static AiProvider configured(String setting, String apiUrl) {
        if (setting == null || setting.isBlank()) {
            return inferredFrom(apiUrl);
        }
        String normalized = setting.strip().toLowerCase(Locale.ROOT);
        for (AiProvider provider : values()) {
            if (provider.setting.equals(normalized)) {
                return provider;
            }
        }
        throw new AiConfigurationException("Unsupported AI_API_PROVIDER: " + setting);
    }

    private static AiProvider inferredFrom(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }
        String normalizedUrl = apiUrl.toLowerCase(Locale.ROOT);
        if (normalizedUrl.contains("generativelanguage.googleapis.com")) {
            return GEMINI;
        }
        if (normalizedUrl.contains("api.anthropic.com")) {
            return ANTHROPIC;
        }
        return OPENAI_COMPATIBLE;
    }
}
