package io.fahchouchsm.betterGitCore.configuration;

/** Persisted, non-secret BetterGit project configuration. */
public record BetterGitConfiguration(
        int schemaVersion,
        String createdAt,
        String projectPath,
        boolean javaDetected,
        boolean gitAlreadyExisted,
        FeatureSettings settings,
        boolean aiDocumentationAvailable) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
