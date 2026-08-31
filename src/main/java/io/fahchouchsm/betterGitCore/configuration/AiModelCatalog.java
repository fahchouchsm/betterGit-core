package io.fahchouchsm.betterGitCore.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

public final class AiModelCatalog implements AiModelSource {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_MODELS = 200;
    private final HttpClient httpClient;

    public AiModelCatalog() {
        httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public List<String> availableModels(AiProvider provider, URI endpoint, String apiKey)
            throws IOException, InterruptedException {
        HttpRequest request = authorizedRequest(provider, endpoint, apiKey);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI model discovery failed with HTTP " + response.statusCode() + ".");
        }
        return modelIds(provider, response.body());
    }

    private static HttpRequest authorizedRequest(AiProvider provider, URI endpoint, String apiKey) {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(TIMEOUT).GET();
        switch (provider) {
            case GEMINI -> request.header("x-goog-api-key", apiKey);
            case ANTHROPIC -> request.header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
            case OPENAI_COMPATIBLE -> request.header("Authorization", "Bearer " + apiKey);
        }
        return request.build();
    }

    static List<String> modelIds(AiProvider provider, String json) throws IOException {
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            JsonArray models = provider == AiProvider.GEMINI
                    ? requiredArray(response, "models") : requiredArray(response, "data");
            return models.asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .map(model -> provider == AiProvider.GEMINI ? model.get("name") : model.get("id"))
                    .filter(element -> element != null && element.isJsonPrimitive()
                            && element.getAsJsonPrimitive().isString())
                    .map(JsonElement::getAsString)
                    .map(AiModelCatalog::stripGeminiPrefix)
                    .filter(model -> !model.isBlank())
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .limit(MAX_MODELS)
                    .toList();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("AI model discovery returned malformed JSON.", exception);
        }
    }

    private static JsonArray requiredArray(JsonObject response, String name) throws IOException {
        JsonElement models = response.get(name);
        if (models == null || !models.isJsonArray()) {
            throw new IOException("AI model discovery response did not contain " + name + ".");
        }
        return models.getAsJsonArray();
    }

    private static String stripGeminiPrefix(String model) {
        return model.startsWith("models/") ? model.substring("models/".length()) : model;
    }
}
