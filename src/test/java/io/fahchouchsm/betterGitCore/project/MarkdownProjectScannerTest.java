package io.fahchouchsm.betterGitCore.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownProjectScannerTest {
    @TempDir
    Path projectPath;

    @Test
    void scansMarkdownAndExcludesGeneratedDirectories() throws Exception {
        Files.writeString(projectPath.resolve("README.md"), "included documentation");
        Files.createDirectories(projectPath.resolve("docs"));
        Files.writeString(projectPath.resolve("docs/setup.md"), "included setup");
        Files.createDirectories(projectPath.resolve("target"));
        Files.writeString(projectPath.resolve("target/generated.md"), "excluded target");
        Files.createDirectories(projectPath.resolve(".bettergit"));
        Files.writeString(projectPath.resolve(".bettergit/general.md"), "excluded output");

        String markdownContext = new MarkdownProjectScanner().scan(projectPath);

        assertTrue(markdownContext.contains("included documentation"));
        assertTrue(markdownContext.contains("included setup"));
        assertFalse(markdownContext.contains("excluded target"));
        assertFalse(markdownContext.contains("excluded output"));
    }
}
