package io.fahchouchsm.betterGitCore.configuration;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** AI credentials and endpoint settings held in memory only. */
public record AiConfiguration(String apiKey, String model, String apiUrl) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isComplete() {
        return hasApiKey() && hasText(model) && validEndpoint();
    }

    public URI resolvedEndpoint() {
        if (!isComplete()) {
            throw new IllegalStateException("AI configuration is incomplete");
        }
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        return URI.create(apiUrl.replace("{model}", encodedModel));
    }

    private boolean validEndpoint() {
        if (!hasText(apiUrl)) {
            return false;
        }
        try {
            URI endpoint = URI.create(apiUrl.replace("{model}", "configured-model"));
            return endpoint.getHost() != null
                    && ("http".equalsIgnoreCase(endpoint.getScheme())
                    || "https".equalsIgnoreCase(endpoint.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
