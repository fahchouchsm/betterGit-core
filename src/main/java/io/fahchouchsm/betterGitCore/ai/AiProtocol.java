package io.fahchouchsm.betterGitCore.ai;

import io.fahchouchsm.betterGitCore.configuration.AiProvider;

import java.net.http.HttpRequest;

sealed interface AiProtocol permits AnthropicProtocol, GeminiProtocol, OpenAiCompatibleProtocol {
    HttpRequest request(AiApiRequest request);

    String outputText(String json) throws AiResponseException;

    static AiProtocol forProvider(AiProvider provider) {
        return switch (provider) {
            case GEMINI -> new GeminiProtocol();
            case ANTHROPIC -> new AnthropicProtocol();
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleProtocol();
        };
    }
}
