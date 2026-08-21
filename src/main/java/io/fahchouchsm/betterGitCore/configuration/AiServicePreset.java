package io.fahchouchsm.betterGitCore.configuration;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

enum AiServicePreset {
    OPENAI(
            "OpenAI",
            AiProvider.OPENAI_COMPATIBLE,
            new ServiceUrls(
                    "https://api.openai.com/v1/chat/completions",
                    "https://api.openai.com/v1/models",
                    "https://platform.openai.com/api-keys")),
    GEMINI(
            "Google Gemini",
            AiProvider.GEMINI,
            new ServiceUrls(
                    "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
                    "https://generativelanguage.googleapis.com/v1beta/models",
                    "https://aistudio.google.com/app/apikey")),
    ANTHROPIC(
            "Anthropic Claude",
            AiProvider.ANTHROPIC,
            new ServiceUrls(
                    "https://api.anthropic.com/v1/messages",
                    "https://api.anthropic.com/v1/models",
                    "https://console.anthropic.com/settings/keys")),
    OPENROUTER(
            "OpenRouter",
            AiProvider.OPENAI_COMPATIBLE,
            new ServiceUrls(
                    "https://openrouter.ai/api/v1/chat/completions",
                    "https://openrouter.ai/api/v1/models",
                    "https://openrouter.ai/settings/keys")),
    GROQ(
            "Groq",
            AiProvider.OPENAI_COMPATIBLE,
            new ServiceUrls(
                    "https://api.groq.com/openai/v1/chat/completions",
                    "https://api.groq.com/openai/v1/models",
                    "https://console.groq.com/keys")),
    CUSTOM(
            "Custom OpenAI-compatible API",
            AiProvider.OPENAI_COMPATIBLE,
            new ServiceUrls(null, null, null));

    private final String label;
    private final AiProvider provider;
    private final ServiceUrls urls;

    AiServicePreset(String label, AiProvider provider, ServiceUrls urls) {
        this.label = label;
        this.provider = provider;
        this.urls = urls;
    }

    String label() {
        return label;
    }

    AiProvider provider() {
        return provider;
    }

    String apiUrl() {
        return urls.api();
    }

    URI modelsEndpoint() {
        return urls.models() == null ? null : URI.create(urls.models());
    }

    String keyUrl() {
        return urls.key();
    }

    static List<String> labels() {
        return Arrays.stream(values()).map(AiServicePreset::label).toList();
    }

    static int selectedIndex(AiConfiguration configuration) {
        AiServicePreset[] presets = values();
        for (int index = 0; index < presets.length; index++) {
            String presetUrl = presets[index].apiUrl();
            if (presetUrl != null && presetUrl.equals(configuration.apiUrl())) {
                return index;
            }
        }
        return CUSTOM.ordinal();
    }

    boolean matches(AiConfiguration configuration) {
        if (configuration.provider() != provider) {
            return false;
        }
        return apiUrl() == null
                ? selectedIndex(configuration) == CUSTOM.ordinal()
                : apiUrl().equals(configuration.apiUrl());
    }

    private record ServiceUrls(String api, String models, String key) {
    }
}
