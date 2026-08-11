package io.fahchouchsm.betterGitCore.ai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /** Creates a client from AI_API_KEY, AI_MODEL, and AI_API_URL_TEMPLATE. */
    public static AiSystem fromEnvironment() {
        return fromConfiguration(System.getenv());
    }

    /** Creates a client from the supplied AI_API_KEY, AI_MODEL, and AI_API_URL_TEMPLATE values. */
    public static AiSystem fromConfiguration(Map<String, String> configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        String apiKey = requireText(configuration.get("AI_API_KEY"), "AI_API_KEY");
        String model = requireText(configuration.get("AI_MODEL"), "AI_MODEL");
        String urlTemplate = requireText(configuration.get("AI_API_URL_TEMPLATE"), "AI_API_URL_TEMPLATE");
        if (!urlTemplate.contains("{model}")) {
            throw new IllegalArgumentException("AI_API_URL_TEMPLATE must contain {model}");
        }
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        return new AiSystem(apiKey, URI.create(urlTemplate.replace("{model}", encodedModel)));
    }

    /**
     * @param input the prompt to send to the AI
     * @return the text produced by the AI
     */
    public String generate(String input) throws IOException, InterruptedException {
        String body = "{\"contents\":[{\"parts\":[{\"text\":\""
                + jsonEscape(requireText(input, "input")) + "\"}]}]}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(90))
                .header("X-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiRequestException(response.statusCode(), response.body());
        }
        String output = extractOutputText(response.body());
        if (output.isEmpty()) {
            throw new IOException("The AI response did not contain text output.");
        }
        return output;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static String extractOutputText(String json) throws IOException {
        StringBuilder output = new StringBuilder();
        int position = 0;
        while ((position = json.indexOf("\"text\":", position)) >= 0) {
            int textKey = position;
            int quote = json.indexOf('"', textKey + 7);
            if (quote < 0) {
                throw new IOException("Invalid JSON text output from AI service.");
            }
            int end = findStringEnd(json, quote + 1);
            output.append(unescapeJson(json.substring(quote + 1, end)));
            position = end + 1;
        }
        return output.toString();
    }

    private static int findStringEnd(String value, int start) throws IOException {
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"' && !escaped) return i;
            escaped = character == '\\' && !escaped;
            if (character != '\\') escaped = false;
        }
        throw new IOException("Invalid JSON string from AI service.");
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unescapeJson(String value) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character != '\\') { result.append(character); continue; }
            if (++i == value.length()) throw new IOException("Invalid JSON escape sequence.");
            switch (value.charAt(i)) {
                case '"' -> result.append('"'); case '\\' -> result.append('\\');
                case '/' -> result.append('/'); case 'b' -> result.append('\b');
                case 'f' -> result.append('\f'); case 'n' -> result.append('\n');
                case 'r' -> result.append('\r'); case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) throw new IOException("Invalid Unicode JSON escape.");
                    try { result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16)); }
                    catch (NumberFormatException error) { throw new IOException("Invalid Unicode JSON escape.", error); }
                    i += 4;
                }
                default -> throw new IOException("Invalid JSON escape sequence.");
            }
        }
        return result.toString();
    }
}
