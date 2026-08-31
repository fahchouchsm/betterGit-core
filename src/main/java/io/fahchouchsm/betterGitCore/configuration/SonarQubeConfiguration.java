package io.fahchouchsm.betterGitCore.configuration;

import java.net.URI;
import java.util.Optional;

public record SonarQubeConfiguration(
        String serverUrl,
        String projectKey,
        String token,
        SonarQubeTrigger trigger,
        java.util.List<String> branches,
        SonarQubeFailurePolicy failurePolicy) {

    public Optional<String> validationError() {
        if (!validServerUrl()) {
            return Optional.of("SonarQube server URL must be a valid HTTP or HTTPS URL.");
        }
        if (projectKey == null || !projectKey.matches("[A-Za-z0-9_.:-]{1,400}")) {
            return Optional.of("SonarQube project key must use letters, digits, dash, underscore, period, or colon.");
        }
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
            return Optional.of("SonarQube token is missing or invalid.");
        }
        return Optional.empty();
    }

    private boolean validServerUrl() {
        if (serverUrl == null || serverUrl.isBlank()) {
            return false;
        }
        try {
            URI server = URI.create(serverUrl);
            return ("http".equalsIgnoreCase(server.getScheme()) || "https".equalsIgnoreCase(server.getScheme()))
                    && server.getHost() != null && server.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
