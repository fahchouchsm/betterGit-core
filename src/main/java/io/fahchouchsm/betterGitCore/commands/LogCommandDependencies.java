package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.history.GitHistoryReader;
import io.fahchouchsm.betterGitCore.history.HistoryDateParser;
import io.fahchouchsm.betterGitCore.history.HistoryJsonRenderer;
import io.fahchouchsm.betterGitCore.history.HistoryTextRenderer;

record LogCommandDependencies(
        GitHistoryReader reader,
        HistoryTextRenderer textRenderer,
        HistoryJsonRenderer jsonRenderer,
        HistoryDateParser dateParser,
        ConsolePort console) {
}
