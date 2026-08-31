package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeCredentialStore;

import java.util.Map;

record FeaturesCommandDependencies(
        BetterGitConfigurationLoader configurationLoader,
        BetterGitFileStore fileStore,
        ConsolePort console,
        SonarQubeConfigurationLoader sonarQubeConfigurationLoader,
        SonarQubeCredentialStore sonarQubeCredentialStore,
        Map<String, String> environment) {
}
