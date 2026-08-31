package io.fahchouchsm.betterGitCore.commands;

import java.io.IOException;
import java.nio.file.Path;

interface GitMergeExecutor {
    int merge(Path projectPath, String sourceBranch, String message) throws IOException, InterruptedException;

    int beginGatedMerge(Path projectPath, String sourceBranch) throws IOException, InterruptedException;

    boolean hasPendingMerge(Path projectPath) throws IOException, InterruptedException;

    int completeGatedMerge(Path projectPath, String message) throws IOException, InterruptedException;

    void abortGatedMerge(Path projectPath) throws IOException, InterruptedException;
}
