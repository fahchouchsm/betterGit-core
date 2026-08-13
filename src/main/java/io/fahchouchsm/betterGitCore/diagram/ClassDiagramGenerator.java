package io.fahchouchsm.betterGitCore.diagram;

import java.io.IOException;
import java.nio.file.Path;

/** Replaceable boundary for BetterGit class-diagram generation. */
public interface ClassDiagramGenerator {
    void generateSvg(Path javaSource, Path outputFile) throws IOException, InterruptedException;
}
