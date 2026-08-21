package io.fahchouchsm.betterGitCore.configuration;

/** Optional BetterGit features configured for future commit commands. */
public record FeatureSettings(
        boolean classDiagramOnCommit,
        boolean testDurationTracking,
        boolean sonarQubeDocumentation) {

    public static FeatureSettings disabled() {
        return new FeatureSettings(false, false, false);
    }
}
