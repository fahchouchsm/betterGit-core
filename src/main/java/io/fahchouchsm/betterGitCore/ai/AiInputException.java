package io.fahchouchsm.betterGitCore.ai;

/** Indicates that the requested AI input is missing or invalid. */
public final class AiInputException extends IllegalArgumentException {
    public AiInputException(String message) {
        super(message);
    }
}
