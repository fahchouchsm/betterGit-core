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

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public BetterGitConfiguration {
        settings = settings == null ? FeatureSettings.disabled() : settings;
        ai = ai == null ? AiCommitSettings.disabled(null) : ai;
    }
}
