package io.fahchouchsm.betterGitCore.configuration;

/** Persisted, non-secret BetterGit project configuration. */
public record BetterGitConfiguration(
        int schemaVersion,
        String createdAt,
        String projectPath,
        boolean javaDetected,
        boolean gitAlreadyExisted,
        FeatureSettings settings,
        boolean aiDocumentationAvailable,
        AiCommitSettings ai) {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    public BetterGitConfiguration {
        settings = settings == null ? FeatureSettings.disabled() : settings;
        ai = ai == null ? AiCommitSettings.disabled(null) : ai;
    }

    public BetterGitConfiguration withAiModel(String model) {
        return new BetterGitConfiguration(
                schemaVersion, createdAt, projectPath, javaDetected, gitAlreadyExisted, settings,
                aiDocumentationAvailable, new AiCommitSettings(ai.commitReportEnabled(), ai.memoryEnabled(), model));
    }

    public BetterGitConfiguration withAiSettings(AiCommitSettings updatedAi) {
        return new BetterGitConfiguration(
                CURRENT_SCHEMA_VERSION, createdAt, projectPath, javaDetected, gitAlreadyExisted, settings,
                aiDocumentationAvailable, updatedAi);
    }

    public BetterGitConfiguration withSettings(FeatureSettings updatedSettings) {
        return new BetterGitConfiguration(
                CURRENT_SCHEMA_VERSION, createdAt, projectPath, javaDetected, gitAlreadyExisted, updatedSettings,
                aiDocumentationAvailable, ai);
    }
}
