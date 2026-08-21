package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.AiSetupService;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;

import java.util.Map;

record AiSetupCommandDependencies(
        AiConfigurationLoader aiConfigurationLoader,
        AiSetupService setupService,
        BetterGitConfigurationLoader configurationLoader,
        BetterGitFileStore fileStore,
        ConsolePort console,
        Map<String, String> environment) {
}
