package io.fahchouchsm.betterGitCore.ai;

import java.io.IOException;

/** Indicates that the AI service returned a successful but unusable response. */
public final class AiResponseException extends IOException {
    public AiResponseException(String message) {
        super(message);
    }

    public AiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
