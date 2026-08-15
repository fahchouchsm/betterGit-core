package io.fahchouchsm.betterGitCore.configuration;

public record AiCommitSettings(
        boolean commitReportEnabled,
        boolean memoryEnabled,
        String model) {

    public static AiCommitSettings disabled(String model) {
        return new AiCommitSettings(false, false, model);
    }

    public boolean hasModel() {
        return model != null && !model.isBlank();
    }
}
