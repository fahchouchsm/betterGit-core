package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commitreport.CommitDataSource;
import io.fahchouchsm.betterGitCore.documentation.AiTextGenerator;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

/** Runtime dependencies supplied to one CLI invocation. */
public record CommandRuntime(
        Path projectPath,
        ConsolePort console,
        RepositoryAccess repositoryAccess,
        CommitDataSource commitDataSource,
        CommitExecutor commitExecutor,
        Map<String, String> environment,
        Clock clock,
        AiTextGenerator aiTextGenerator) {

    @Override
    public String toString() {
        return "CommandRuntime[projectPath=" + projectPath + ", environment=[REDACTED]]";
    }
}
