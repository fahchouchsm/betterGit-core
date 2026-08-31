package io.fahchouchsm.betterGitCore.configuration;

/** Optional BetterGit behaviors applied to project commits and documentation. */
public record FeatureSettings(
        boolean classDiagramOnCommit,
        boolean testDurationTracking,
        boolean sonarQubeDocumentation,
        SonarQubeSettings sonarQube) {

    public FeatureSettings {
        sonarQube = sonarQube == null ? SonarQubeSettings.defaults() : sonarQube;
    }

    public FeatureSettings(
            boolean classDiagramOnCommit,
            boolean testDurationTracking,
            boolean sonarQubeDocumentation) {
        this(classDiagramOnCommit, testDurationTracking, sonarQubeDocumentation, SonarQubeSettings.defaults());
    }

    public static FeatureSettings disabled() {
        return new FeatureSettings(false, false, false);
    }

    public FeatureSettings withSonarQube(SonarQubeSettings settings) {
        return new FeatureSettings(classDiagramOnCommit, testDurationTracking, true, settings);
    }
}
