package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeCredentialStore;

import java.util.Map;

record SonarQubeSettingsDependencies(
        SonarQubeConfigurationLoader loader,
        SonarQubeCredentialStore credentialStore,
        BetterGitFileStore fileStore,
        ConsolePort console,
        Map<String, String> environment) {
}
