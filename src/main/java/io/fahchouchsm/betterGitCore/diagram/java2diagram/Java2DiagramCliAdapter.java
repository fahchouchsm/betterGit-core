package io.fahchouchsm.betterGitCore.diagram.java2diagram;

import io.fahchouchsm.betterGitCore.diagram.ClassDiagramGenerator;

import java.io.IOException;
import java.nio.file.Path;

/** Runs the separately installed CLI because Java2Diagram has no documented published Maven artifact. */
public final class Java2DiagramCliAdapter implements ClassDiagramGenerator {
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
