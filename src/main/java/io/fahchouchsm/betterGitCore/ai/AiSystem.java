package io.fahchouchsm.betterGitCore.ai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Sends text to a Gemini generateContent endpoint and returns the generated text.
 * Connection settings are supplied by the caller or environment variables.
 */
public final class AiSystem {
    private final String apiKey;
    private final URI endpoint;
    private final HttpClient httpClient;

    public AiSystem(String apiKey, URI endpoint) {
        this.apiKey = requireText(apiKey, "apiKey");
        this.endpoint = requireEndpoint(endpoint);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /** Creates a client from AI_API_KEY, AI_MODEL, and AI_API_URL_TEMPLATE. */
    public static AiSystem fromEnvironment() {
        return fromConfiguration(System.getenv());
    }

    /** Creates a client from the supplied AI_API_KEY, AI_MODEL, and AI_API_URL_TEMPLATE values. */
    public static AiSystem fromConfiguration(Map<String, String> configuration) {
        if (configuration == null) {
            throw new AiConfigurationException("configuration must not be null");
        }
        String apiKey = requireText(configuration.get("AI_API_KEY"), "AI_API_KEY");
        String model = requireText(configuration.get("AI_MODEL"), "AI_MODEL");
        String urlTemplate = requireText(configuration.get("AI_API_URL_TEMPLATE"), "AI_API_URL_TEMPLATE");
        if (!urlTemplate.contains("{model}")) {
            throw new AiConfigurationException("AI_API_URL_TEMPLATE must contain {model}");
        }
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        try {
            return new AiSystem(apiKey, URI.create(urlTemplate.replace("{model}", encodedModel)));
        } catch (IllegalArgumentException exception) {
            throw new AiConfigurationException("AI_API_URL_TEMPLATE must resolve to a valid HTTP URL", exception);
        }
    }

    /**
     * @param input the prompt to send to the AI
     * @return the text produced by the AI
     */
    public String generate(String input) throws IOException, InterruptedException {
        String body = "{\"contents\":[{\"parts\":[{\"text\":\""
                + jsonEscape(requireInput(input)) + "\"}]}]}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(90))
                .header("X-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw new AiConnectionException("The AI service did not respond before the request timeout.", exception);
        } catch (IOException exception) {
            throw new AiConnectionException("Could not connect to the AI service.", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiRequestException(response.statusCode(), response.body());
        }
        return extractOutputText(response.body());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AiConfigurationException(name + " must not be blank");
        }
        return value;
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
        return endpoint;
    }

    static String extractOutputText(String json) throws AiResponseException {
        if (json == null || json.isBlank()) {
            throw new AiResponseException("The AI service returned an empty response.");
        }
        StringBuilder output = new StringBuilder();
        int position = 0;
        while ((position = json.indexOf("\"text\":", position)) >= 0) {
            int textKey = position;
            int quote = json.indexOf('"', textKey + 7);
            if (quote < 0) {
                throw new AiResponseException("The AI service returned malformed text output.");
            }
            int end = findStringEnd(json, quote + 1);
            output.append(unescapeJson(json.substring(quote + 1, end)));
            position = end + 1;
        }
        if (output.isEmpty()) {
            throw new AiResponseException("The AI response did not contain generated text.");
        }
        return output.toString();
    }

    private static int findStringEnd(String value, int start) throws AiResponseException {
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' && !escaped) return i;
            escaped = character == '\\' && !escaped;
            if (character != '\\') escaped = false;
        }
        throw new AiResponseException("The AI service returned an unterminated JSON string.");
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescapeJson(String value) throws AiResponseException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character != '\\') { result.append(character); continue; }
            if (++i == value.length()) throw new AiResponseException("The AI service returned an invalid JSON escape sequence.");
            switch (value.charAt(i)) {
                case '"' -> result.append('"'); case '\\' -> result.append('\\');
                case '/' -> result.append('/'); case 'b' -> result.append('\b');
                case 'f' -> result.append('\f'); case 'n' -> result.append('\n');
                case 'r' -> result.append('\r'); case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) throw new AiResponseException("The AI service returned an invalid Unicode escape.");
                    try { result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16)); }
                    catch (NumberFormatException error) { throw new AiResponseException("The AI service returned an invalid Unicode escape.", error); }
                    i += 4;
                }
                default -> throw new AiResponseException("The AI service returned an invalid JSON escape sequence.");
            }
        }
        return result.toString();
    }
}
