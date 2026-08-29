package io.fahchouchsm.betterGitCore.sonarqube;

import io.fahchouchsm.betterGitCore.configuration.SonarQubeFailurePolicy;

import java.util.List;

public record SonarAnalysisOutcome(
        Status status,
        List<SonarCondition> conditions,
        String dashboardUrl,
        String failureMessage,
        SonarQubeFailurePolicy failurePolicy) {

    public SonarAnalysisOutcome {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public boolean passed() {
        return status == Status.PASSED;
    }

    public enum Status {
        PASSED,
        QUALITY_GATE_FAILED,
        ANALYSIS_FAILED
    }
}
