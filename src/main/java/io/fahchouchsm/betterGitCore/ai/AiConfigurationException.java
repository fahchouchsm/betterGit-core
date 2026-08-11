package io.fahchouchsm.betterGitCore.ai;

/** Indicates that required local AI configuration is missing or invalid. */
public final class AiConfigurationException extends IllegalArgumentException {
    public AiConfigurationException(String message) {
        super(message);
    }

    public AiConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
