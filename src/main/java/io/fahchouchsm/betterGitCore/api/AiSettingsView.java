package io.fahchouchsm.betterGitCore.api;

import io.fahchouchsm.betterGitCore.configuration.AiProvider;

public record AiSettingsView(
        AiProvider provider,
        String model,
        String apiUrl,
        boolean credentialConfigured,
        boolean commitReports,
        boolean memory) {
}
