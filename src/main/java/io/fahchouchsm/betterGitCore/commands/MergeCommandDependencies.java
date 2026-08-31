package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;

record MergeCommandDependencies(
        GitMergeExecutor mergeExecutor,
        BranchReader branchReader,
        SonarQubeGate sonarQubeGate,
        ConsolePort console) {
}
