package io.fahchouchsm.betterGitCore.testduration;

import java.time.Duration;

public record TestRun(String command, Duration duration, int exitCode) {
    public boolean passed() {
        return exitCode == 0;
    }
}
