package io.fahchouchsm.betterGitCore.ai;

import java.io.IOException;

/** Indicates that the AI provider rejected or could not process a request. */
public final class AiRequestException extends IOException {
    private final int statusCode;

    public AiRequestException(int statusCode, String responseBody) {
        super("AI request failed with HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
