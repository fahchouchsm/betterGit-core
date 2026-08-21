package io.fahchouchsm.betterGitCore.testduration;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface TestSuiteRunner {
    TestRun run(Path committedProject) throws IOException, InterruptedException;
}
