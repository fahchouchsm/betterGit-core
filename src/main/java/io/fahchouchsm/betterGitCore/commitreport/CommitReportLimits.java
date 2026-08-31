package io.fahchouchsm.betterGitCore.commitreport;

import java.util.Map;

public record CommitReportLimits(int maximumPromptCharacters) {
    public static final String MAX_INPUT_ENVIRONMENT = "BETTERGIT_AI_MAX_INPUT_CHARS";
    public static final int DEFAULT_MAXIMUM = 60_000;
    private static final int MINIMUM = 8_000;
    private static final int MAXIMUM = 500_000;

    public CommitReportLimits {
        if (maximumPromptCharacters < 256) {
            throw new IllegalArgumentException("maximumPromptCharacters must be at least 256");
        }
    }

    public static CommitReportLimits fromEnvironment(Map<String, String> environment) {
        String configuredLimit = environment.get(MAX_INPUT_ENVIRONMENT);
        if (configuredLimit == null || configuredLimit.isBlank()) {
            return new CommitReportLimits(DEFAULT_MAXIMUM);
        }
        try {
            int parsedLimit = Integer.parseInt(configuredLimit);
            return new CommitReportLimits(Math.max(MINIMUM, Math.min(MAXIMUM, parsedLimit)));
        } catch (NumberFormatException exception) {
            return new CommitReportLimits(DEFAULT_MAXIMUM);
        }
    }
}
