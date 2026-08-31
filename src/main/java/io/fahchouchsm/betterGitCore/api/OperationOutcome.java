package io.fahchouchsm.betterGitCore.api;

public record OperationOutcome(int exitCode) {
    public boolean succeeded() {
        return exitCode == 0;
    }
}
