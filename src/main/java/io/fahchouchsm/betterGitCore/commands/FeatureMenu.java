package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;

import java.io.IOException;
import java.util.List;

public final class FeatureMenu {
    private static final List<String> CHOICES = List.of(
            "Save a class diagram on each commit",
            "Track test duration on each commit",
            "Generate SonarQube documentation");

    private FeatureMenu() {
    }

    public static FeatureSettings select(
            ConsolePort console, String question, FeatureSettings currentSettings) throws IOException {
        List<Boolean> selections = console.chooseMany(
                question, CHOICES, selectedFeatures(currentSettings));
        return new FeatureSettings(selections.get(0), selections.get(1), selections.get(2));
    }

    private static List<Boolean> selectedFeatures(FeatureSettings settings) {
        return List.of(
                settings.classDiagramOnCommit(),
                settings.testDurationTracking(),
                settings.sonarQubeDocumentation());
    }
}
