package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfiguration;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeFailurePolicy;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeSettings;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeTrigger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

final class SonarQubeSettingsCollector {
    private static final List<String> TRIGGERS = List.of(
            "Commits only", "Merges only", "Commits and merges");
    private static final List<String> FAILURE_POLICIES = List.of(
            "Ask for approval", "Cancel automatically");
    private final SonarQubeSettingsDependencies dependencies;

    SonarQubeSettingsCollector(SonarQubeSettingsDependencies dependencies) {
        this.dependencies = dependencies;
    }

    SonarQubeSettings configure(Path projectPath, SonarQubeSettings current) throws IOException {
        SonarQubeConfiguration loaded = dependencies.loader().load(
                projectPath, current, dependencies.environment());
        String serverUrl = text("SonarQube server URL", loaded.serverUrl());
        String projectKey = text("SonarQube project key", projectKey(projectPath, loaded.projectKey()));
        SonarQubeTrigger trigger = trigger(current.trigger());
        List<String> branches = branches(current.branches());
        SonarQubeFailurePolicy failurePolicy = failurePolicy(current.failurePolicy());
        saveTokenIfEntered(projectPath, loaded.token());
        dependencies.fileStore().ensureEnvIgnored(projectPath);
        return new SonarQubeSettings(serverUrl, projectKey, trigger, branches, failurePolicy);
    }

    private String text(String label, String current) throws IOException {
        String entered = dependencies.console().readLine(label + " [" + current + "]: ").trim();
        return entered.isEmpty() ? current : entered;
    }

    private SonarQubeTrigger trigger(SonarQubeTrigger current) throws IOException {
        int selected = dependencies.console().chooseOne(
                "Run SonarQube on", TRIGGERS, triggerIndex(current));
        return SonarQubeTrigger.values()[selected];
    }

    private List<String> branches(List<String> current) throws IOException {
        String defaultBranches = current.isEmpty() ? "all" : String.join(",", current);
        String entered = dependencies.console().readLine(
                "Branches (comma-separated, blank keeps " + defaultBranches + "): ").trim();
        if (entered.isEmpty()) {
            return current;
        }
        if ("all".equalsIgnoreCase(entered)) {
            return List.of();
        }
        return Arrays.stream(entered.split(",")).map(String::strip).filter(name -> !name.isEmpty()).toList();
    }

    private SonarQubeFailurePolicy failurePolicy(SonarQubeFailurePolicy current) throws IOException {
        int defaultChoice = current == SonarQubeFailurePolicy.ASK_FOR_APPROVAL ? 0 : 1;
        int selected = dependencies.console().chooseOne(
                "When the quality gate blocks", FAILURE_POLICIES, defaultChoice);
        return selected == 0 ? SonarQubeFailurePolicy.ASK_FOR_APPROVAL : SonarQubeFailurePolicy.CANCEL;
    }

    private void saveTokenIfEntered(Path projectPath, String existingToken) throws IOException {
        String prompt = existingToken == null || existingToken.isBlank()
                ? "SonarQube token: "
                : "SonarQube token (leave blank to keep current): ";
        String token = dependencies.console().readSecret(prompt).trim();
        if (!token.isEmpty()) {
            dependencies.credentialStore().update(projectPath, token);
            dependencies.console().success("SonarQube token saved locally and masked.");
        } else if (existingToken == null || existingToken.isBlank()) {
            dependencies.console().warning(
                    "No SonarQube token is configured; analysis will block until one is supplied.");
        }
    }

    private static int triggerIndex(SonarQubeTrigger trigger) {
        return switch (trigger) {
            case COMMITS -> 0;
            case MERGES -> 1;
            case COMMITS_AND_MERGES -> 2;
        };
    }

    private static String projectKey(Path projectPath, String current) {
        return current == null || current.isBlank()
                ? projectPath.getFileName().toString()
                : current;
    }
}
