package io.fahchouchsm.betterGitCore.ai;

import java.io.IOException;

/** Indicates that the AI service could not be reached or did not respond in time. */
public final class AiConnectionException extends IOException {
    public AiConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
