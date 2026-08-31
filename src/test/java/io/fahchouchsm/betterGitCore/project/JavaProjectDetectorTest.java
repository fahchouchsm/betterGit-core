package io.fahchouchsm.betterGitCore.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaProjectDetectorTest {
    private final JavaProjectDetector detector = new JavaProjectDetector();

    @TempDir
    Path projectPath;

    @Test
    void detectsJavaBuildFilesAndSourceDirectories() throws Exception {
        assertFalse(detector.isJavaProject(projectPath));

        Files.writeString(projectPath.resolve("pom.xml"), "<project/>");
        assertTrue(detector.isJavaProject(projectPath));

        Files.delete(projectPath.resolve("pom.xml"));
        Files.createDirectories(projectPath.resolve("src/test/java"));
        assertTrue(detector.isJavaProject(projectPath));
    }
}
