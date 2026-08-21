package io.fahchouchsm.betterGitCore.testduration;

import java.nio.file.Path;
import java.time.Duration;

public final class TestSuiteFailedException extends Exception {
    private final Path reportPath;
    private final Duration duration;

    TestSuiteFailedException(Path reportPath, Duration duration, int exitCode) {
        super("Tests exited with code " + exitCode);
        this.reportPath = reportPath;
        this.duration = duration;
    }

    public Path reportPath() {
        return reportPath;
    }

    public Duration duration() {
        return duration;
    }
}
