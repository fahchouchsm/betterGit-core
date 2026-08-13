package io.fahchouchsm.betterGitCore.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Sends text to a Gemini generateContent endpoint and returns the generated text.
 * Connection settings are supplied by the caller or environment variables.
 */
public final class AiSystem {
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final String apiKey;
    private final URI endpoint;
    private final HttpClient httpClient;

    public AiSystem(String apiKey, URI endpoint) {
        this.apiKey = requireText(apiKey, "apiKey");
        this.endpoint = requireEndpoint(endpoint);
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECTION_TIMEOUT).build();
    }

    /** Creates a client from AI_API_KEY, AI_API_MODEL, and AI_API_URL. */
    public static AiSystem fromEnvironment() {
        return fromConfiguration(System.getenv());
    }

    /** Creates a client from current AI settings, with legacy names accepted for compatibility. */
    public static AiSystem fromConfiguration(Map<String, String> configuration) {
        if (configuration == null) {
            throw new AiConfigurationException("configuration must not be null");
        }
        String apiKey = requireText(configuration.get("AI_API_KEY"), "AI_API_KEY");
        String model = requireText(preferredSetting(configuration, "AI_API_MODEL", "AI_MODEL"), "AI_API_MODEL");
        String apiUrl = requireText(preferredSetting(configuration, "AI_API_URL", "AI_API_URL_TEMPLATE"), "AI_API_URL");
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        try {
            return new AiSystem(apiKey, URI.create(apiUrl.replace("{model}", encodedModel)));
        } catch (IllegalArgumentException exception) {
            throw new AiConfigurationException("AI_API_URL must resolve to a valid HTTP URL", exception);
        }
    }

    private static String preferredSetting(Map<String, String> settings, String currentName, String legacyName) {
        return settings.containsKey(currentName) ? settings.get(currentName) : settings.get(legacyName);
    }

    /**
     * @param input the prompt to send to the AI
     * @return the text produced by the AI
     */
    public String generate(String input) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("X-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createRequestBody(requireInput(input))))
                .build();
        HttpResponse<String> response = send(request);
        ensureSuccess(response);
        return extractOutputText(response.body());
    }

    private HttpResponse<String> send(HttpRequest request) throws AiConnectionException, InterruptedException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw new AiConnectionException("The AI service did not respond before the request timeout.", exception);
        } catch (IOException exception) {
            throw new AiConnectionException("Could not connect to the AI service.", exception);
        }
    }

    private static void ensureSuccess(HttpResponse<String> response) throws AiRequestException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiRequestException(response.statusCode(), response.body());
        }
    }

    private static String createRequestBody(String input) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", input);
        JsonArray parts = new JsonArray();
        parts.add(textPart);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject request = new JsonObject();
        request.add("contents", contents);
        return request.toString();
    }

    private static String requireText(String configurationText, String name) {
        if (configurationText == null || configurationText.isBlank()) {
            throw new AiConfigurationException(name + " must not be blank");
        }
        return configurationText;
    }

    private static String requireInput(String input) {
        if (input == null || input.isBlank()) {
            throw new AiInputException("input must not be blank");
        }
        return input;
    }

    private static URI requireEndpoint(URI endpoint) {
        if (endpoint == null) {
            throw new AiConfigurationException("endpoint must not be null");
        }
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new AiConfigurationException("endpoint must use HTTP or HTTPS");
        }
        if (endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw new AiConfigurationException("endpoint must include a host");
        }
        return endpoint;
    }

    static String extractOutputText(String json) throws AiResponseException {
        if (json == null || json.isBlank()) {
            throw new AiResponseException("The AI service returned an empty response.");
        }
        try {
            return generatedText(JsonParser.parseString(json).getAsJsonObject());
        } catch (JsonParseException | IllegalStateException exception) {
            throw new AiResponseException("The AI service returned malformed JSON.", exception);
        }
    }

    private static String generatedText(JsonObject response) throws AiResponseException {
        StringBuilder generatedText = new StringBuilder();
        for (JsonElement candidateElement : requiredArray(response, "candidates")) {
            JsonObject candidate = requiredObject(candidateElement, "candidate");
            JsonObject content = requiredObject(candidate.get("content"), "candidate content");
            appendPartText(generatedText, requiredArray(content, "parts"));
        }
        if (generatedText.isEmpty()) {
            throw new AiResponseException("The AI response did not contain generated text.");
        }
        return generatedText.toString();
    }

    private static void appendPartText(StringBuilder generatedText, JsonArray parts) throws AiResponseException {
        for (JsonElement partElement : parts) {
            JsonObject part = requiredObject(partElement, "candidate part");
            JsonElement text = part.get("text");
            if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                generatedText.append(text.getAsString());
            }
        }
    }

    private static JsonArray requiredArray(JsonObject parent, String fieldName) throws AiResponseException {
        JsonElement field = parent.get(fieldName);
        if (field == null || !field.isJsonArray()) {
            throw new AiResponseException("The AI response did not contain " + fieldName + ".");
        }
        return field.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonElement element, String description) throws AiResponseException {
        if (element == null || !element.isJsonObject()) {
            throw new AiResponseException("The AI response did not contain a valid " + description + ".");
        }
        return element.getAsJsonObject();
    }
}
