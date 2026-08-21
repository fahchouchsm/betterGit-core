package io.fahchouchsm.betterGitCore.configuration;

/** Optional BetterGit behaviors applied to project commits and documentation. */
public record FeatureSettings(
        boolean classDiagramOnCommit,
        boolean testDurationTracking,
        boolean sonarQubeDocumentation) {

    public static FeatureSettings disabled() {
        return new FeatureSettings(false, false, false);
    }
}
