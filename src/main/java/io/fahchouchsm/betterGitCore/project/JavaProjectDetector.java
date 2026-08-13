package io.fahchouchsm.betterGitCore.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Detects Java projects from common build files and source layouts. */
public final class JavaProjectDetector {
    private static final List<String> BUILD_FILES = List.of("pom.xml", "build.gradle", "build.gradle.kts");
    private static final List<String> SOURCE_DIRECTORIES = List.of("src/main/java", "src/test/java");

    public boolean isJavaProject(Path projectPath) {
        return BUILD_FILES.stream().map(projectPath::resolve).anyMatch(Files::isRegularFile)
                || SOURCE_DIRECTORIES.stream().map(projectPath::resolve).anyMatch(Files::isDirectory);
    }
}
