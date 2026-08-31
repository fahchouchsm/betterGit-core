package io.fahchouchsm.betterGitCore.api;

public record MergeRequest(String sourceBranch, String message) {
    public MergeRequest {
        if (sourceBranch == null || sourceBranch.isBlank() || sourceBranch.startsWith("-")) {
            throw new IllegalArgumentException("A valid source branch is required.");
        }
        sourceBranch = sourceBranch.strip();
        message = message == null ? "" : message.strip();
    }
}
