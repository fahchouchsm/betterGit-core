package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;

record FeaturesCommandDependencies(
        BetterGitConfigurationLoader configurationLoader,
        BetterGitFileStore fileStore,
        ConsolePort console) {
}
