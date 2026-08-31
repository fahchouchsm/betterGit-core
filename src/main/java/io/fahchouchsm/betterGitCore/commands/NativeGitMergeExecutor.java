package io.fahchouchsm.betterGitCore.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class NativeGitMergeExecutor implements GitMergeExecutor {
    @Override
    public int merge(Path projectPath, String sourceBranch, String message)
            throws IOException, InterruptedException {
        List<String> arguments = new ArrayList<>(List.of("merge", sourceBranch));
        addMessage(arguments, message);
        return new NativeGitCommand(projectPath).execute(arguments.toArray(String[]::new));
    }

    @Override
    public int beginGatedMerge(Path projectPath, String sourceBranch)
            throws IOException, InterruptedException {
        return new NativeGitCommand(projectPath).execute(
                new String[]{"merge", "--no-commit", "--no-ff", sourceBranch});
    }

    @Override
    public int completeGatedMerge(Path projectPath, String message)
            throws IOException, InterruptedException {
        List<String> arguments = message == null || message.isBlank()
                ? new ArrayList<>(List.of("commit", "--no-edit"))
                : new ArrayList<>(List.of("commit", "-m", message.strip()));
        return new NativeGitCommand(projectPath).execute(arguments.toArray(String[]::new));
    }

    @Override
    public boolean hasPendingMerge(Path projectPath) throws IOException, InterruptedException {
        return new NativeGitCommand(projectPath).execute(
                new String[]{"rev-parse", "-q", "--verify", "MERGE_HEAD"}) == 0;
    }

    @Override
    public void abortGatedMerge(Path projectPath) throws IOException, InterruptedException {
        int exitCode = new NativeGitCommand(projectPath).execute(new String[]{"merge", "--abort"});
        if (exitCode != 0) {
            throw new IOException("Git could not abort the pending merge.");
        }
    }

    private static void addMessage(List<String> arguments, String message) {
        if (message != null && !message.isBlank()) {
            arguments.add("-m");
            arguments.add(message.strip());
        }
    }
}
