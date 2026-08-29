package io.fahchouchsm.betterGitCore.sonarqube;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

public final class HttpSonarQualityGateClient implements SonarQualityGateClient {
    private static final Gson JSON = new Gson();
    private final HttpClient client;

    public HttpSonarQualityGateClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    HttpSonarQualityGateClient(HttpClient client) {
        this.client = client;
    }

    @Override
    public SonarQualityGate fetch(SonarQubeConfiguration configuration)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint(configuration))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", basicToken(configuration.token()))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("SonarQube quality-gate API returned HTTP " + response.statusCode() + ".");
        }
        ApiResponse parsed = parse(response.body());
        if (parsed == null || parsed.projectStatus == null || parsed.projectStatus.status == null) {
            throw new IOException("SonarQube returned an invalid quality-gate response.");
        }
        return new SonarQualityGate(parsed.projectStatus.status, parsed.projectStatus.conditions);
    }

    private static ApiResponse parse(String responseBody) throws IOException {
        try {
            return JSON.fromJson(responseBody, ApiResponse.class);
        } catch (JsonParseException exception) {
            throw new IOException("SonarQube returned malformed quality-gate JSON.", exception);
        }
    }

    private static URI endpoint(SonarQubeConfiguration configuration) throws IOException {
        URI server;
        try {
            server = URI.create(configuration.serverUrl());
        } catch (IllegalArgumentException exception) {
            throw new IOException("SonarQube server URL is invalid.", exception);
        }
        if (!("http".equalsIgnoreCase(server.getScheme()) || "https".equalsIgnoreCase(server.getScheme()))) {
            throw new IOException("SonarQube server URL must use HTTP or HTTPS.");
        }
        String baseUrl = configuration.serverUrl().replaceAll("/+$", "");
        String projectKey = URLEncoder.encode(configuration.projectKey(), StandardCharsets.UTF_8);
        return URI.create(baseUrl + "/api/qualitygates/project_status?projectKey=" + projectKey);
    }

    private static String basicToken(String token) {
        String credentials = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + credentials;
    }

    private static final class ApiResponse {
        private ProjectStatus projectStatus;
    }

    private static final class ProjectStatus {
        private String status;
        private List<SonarCondition> conditions;
    }
}
