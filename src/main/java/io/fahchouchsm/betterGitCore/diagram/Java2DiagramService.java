package io.fahchouchsm.betterGitCore.diagram;

import java.io.IOException;
import java.nio.file.Path;

/** Adapter for the separately installed Java2Diagram command-line application. */
public final class Java2DiagramService implements ClassDiagramService {
    public static final String PROJECT_URL = "https://github.com/fahchouchsm/Java2Diagram";

    @Override
    public void generateSvg(Path javaSource, Path outputFile) throws IOException, InterruptedException {
        Process diagramProcess = new ProcessBuilder(
                "java2diagram",
                javaSource.toAbsolutePath().toString(),
                "-f", "svg",
                "-o", outputFile.toAbsolutePath().toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        int exitCode = diagramProcess.waitFor();
        if (exitCode != 0) {
            throw new IOException("Java2Diagram exited with code " + exitCode);
        }
    }
}
