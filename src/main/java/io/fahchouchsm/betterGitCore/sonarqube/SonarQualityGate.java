package io.fahchouchsm.betterGitCore.sonarqube;

import java.util.List;
import java.util.Objects;

public record SonarQualityGate(String status, List<SonarCondition> conditions) {
    public SonarQualityGate {
        conditions = conditions == null
                ? List.of()
                : conditions.stream().filter(Objects::nonNull).toList();
    }

    public boolean passed() {
        return "OK".equalsIgnoreCase(status);
    }
}
