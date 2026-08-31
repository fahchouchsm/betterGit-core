package io.fahchouchsm.betterGitCore.configuration;

import java.util.List;
import java.util.Objects;

public record SonarQubeSettings(
        String serverUrl,
        String projectKey,
        SonarQubeTrigger trigger,
        List<String> branches,
        SonarQubeFailurePolicy failurePolicy) {

    public SonarQubeSettings {
        serverUrl = blankToDefault(serverUrl, "http://localhost:9000");
        projectKey = blankToNull(projectKey);
        trigger = trigger == null ? SonarQubeTrigger.COMMITS_AND_MERGES : trigger;
        branches = branches == null ? List.of() : branches.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(branch -> !branch.isEmpty())
                .distinct()
                .toList();
        failurePolicy = failurePolicy == null
                ? SonarQubeFailurePolicy.ASK_FOR_APPROVAL
                : failurePolicy;
    }

    public static SonarQubeSettings defaults() {
        return new SonarQubeSettings(
                "http://localhost:9000", null, SonarQubeTrigger.COMMITS_AND_MERGES,
                List.of(), SonarQubeFailurePolicy.ASK_FOR_APPROVAL);
    }

    public boolean appliesToBranch(String branch) {
        return branches.isEmpty() || branches.contains(branch);
    }

    private static String blankToDefault(String text, String defaultText) {
        String normalized = blankToNull(text);
        return normalized == null ? defaultText : normalized;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
