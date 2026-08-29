package io.fahchouchsm.betterGitCore.api;

public record CommitRequest(String message, boolean useAiReport) {
    public CommitRequest {
        message = message == null ? "" : message.strip();
        if (!useAiReport && message.isEmpty()) {
            throw new IllegalArgumentException("A commit message is required when AI reporting is disabled.");
        }
    }
}
