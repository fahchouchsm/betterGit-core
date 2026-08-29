package io.fahchouchsm.betterGitCore.api;

public record OperationEvent(EventSeverity severity, String message) {
    public OperationEvent {
        if (severity == null) {
            throw new IllegalArgumentException("Event severity is required.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Event message is required.");
        }
        message = message.strip();
    }
}
