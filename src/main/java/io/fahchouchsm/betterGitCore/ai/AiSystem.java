package io.fahchouchsm.betterGitCore.ai;

import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;
import io.fahchouchsm.betterGitCore.configuration.AiProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/** Sends text through the protocol selected in the AI configuration. */
public final class AiSystem {
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final AiConfiguration configuration;
    private final URI endpoint;
    private final AiProtocol protocol;
    private final HttpClient httpClient;

    public AiSystem(AiConfiguration configuration) {
        this.configuration = requireComplete(configuration);
        endpoint = requireEndpoint(configuration.resolvedEndpoint());
        protocol = AiProtocol.forProvider(configuration.provider());
        httpClient = HttpClient.newBuilder().connectTimeout(CONNECTION_TIMEOUT).build();
    }

    public AiSystem(String apiKey, URI endpoint) {
        this(legacyConfiguration(apiKey, endpoint));
    }

    public static AiSystem fromEnvironment() {
        return fromConfiguration(System.getenv());
    }

    public static AiSystem fromConfiguration(Map<String, String> settings) {
        if (settings == null) {
            throw new AiConfigurationException("configuration must not be null");
        }
        String apiKey = requiredSetting(settings, "AI_API_KEY");
        String model = requiredPreferredSetting(settings, "AI_API_MODEL", "AI_MODEL");
        String apiUrl = requiredPreferredSetting(settings, "AI_API_URL", "AI_API_URL_TEMPLATE");
        AiProvider provider = AiProvider.configured(settings.get("AI_API_PROVIDER"), apiUrl);
        return new AiSystem(new AiConfiguration(provider, apiKey, model, apiUrl));
    }

    public String generate(String input) throws IOException, InterruptedException {
        String prompt = requireInput(input);
        AiApiRequest apiRequest = new AiApiRequest(
                endpoint, configuration.apiKey(), configuration.model(), prompt, REQUEST_TIMEOUT);
        HttpResponse<String> response = send(protocol.request(apiRequest));
        ensureSuccess(response);
        return protocol.outputText(response.body());
    }

    static String extractOutputText(String json) throws AiResponseException {
        return new GeminiProtocol().outputText(json);
    }

    private HttpResponse<String> send(HttpRequest request)
            throws AiConnectionException, InterruptedException {
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

    private static AiConfiguration requireComplete(AiConfiguration configuration) {
        if (configuration == null || !configuration.isComplete()) {
            throw new AiConfigurationException("AI configuration must be complete");
        }
        return configuration;
    }

    private static AiConfiguration legacyConfiguration(String apiKey, URI endpoint) {
        String validatedKey = requireText(apiKey, "apiKey");
        URI validatedEndpoint = requireEndpoint(endpoint);
        return new AiConfiguration(
                AiProvider.GEMINI, validatedKey, "legacy-model", validatedEndpoint.toString());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AiConfigurationException(name + " must not be blank");
        }
        return value;
    }

    private static String requiredSetting(Map<String, String> settings, String name) {
        String setting = settings.get(name);
        if (setting == null || setting.isBlank()) {
            throw new AiConfigurationException(name + " must not be blank");
        }
        return setting;
    }

    private static String requiredPreferredSetting(
            Map<String, String> settings, String currentName, String legacyName) {
        String setting = settings.containsKey(currentName)
                ? settings.get(currentName) : settings.get(legacyName);
        if (setting == null || setting.isBlank()) {
            throw new AiConfigurationException(currentName + " must not be blank");
        }
        return setting;
    }

    private static String requireInput(String input) {
        if (input == null || input.isBlank()) {
            throw new AiInputException("input must not be blank");
        }
        return input;
    }

    private static URI requireEndpoint(URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getHost().isBlank()) {
            throw new AiConfigurationException("endpoint must include a host");
        }
        if (!"http".equalsIgnoreCase(endpoint.getScheme())
                && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new AiConfigurationException("endpoint must use HTTP or HTTPS");
        }
        return endpoint;
    }
}
