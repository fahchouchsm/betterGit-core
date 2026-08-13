package io.fahchouchsm.betterGitCore.configuration;

/** Persisted, non-secret BetterGit project configuration. */
public record BetterGitConfiguration(
        int schemaVersion,
        String projectType,
        boolean javaDetected,
        boolean gitAlreadyExisted,
        FeatureSettings settings,
        boolean aiDocumentationAvailable,
        String initializedAt,
        String updatedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
