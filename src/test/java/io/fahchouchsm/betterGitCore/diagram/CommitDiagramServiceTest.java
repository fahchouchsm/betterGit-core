package io.fahchouchsm.betterGitCore.diagram;

import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitDiagramServiceTest {
    private static final String COMMIT_HASH = "1234567890123456789012345678901234567890";

    @TempDir
    Path projectPath;

    @Test
    void generatesAndNamesAnSvgForTheCommit() throws Exception {
        configure(new FeatureSettings(true, false, false));
        Path javaSources = Files.createDirectories(projectPath.resolve("src/main/java"));
        Files.writeString(javaSources.resolve("Sample.java"), "class Sample {}\n");
        Path[] generatedSource = new Path[1];
        CommitDiagramService service = service((source, output) -> {
            generatedSource[0] = source;
            Files.writeString(output, "<svg>Sample</svg>\n");
        });

        Optional<CommitDiagramPlan> plan = service.planForCommit(projectPath, COMMIT_HASH);
        service.generate(plan.orElseThrow());

        Path expected = projectPath.resolve(".bettergit/diagrams/" + COMMIT_HASH + ".svg");
        assertEquals(expected, plan.orElseThrow().outputFile());
        assertEquals(javaSources, generatedSource[0]);
        assertTrue(Files.readString(expected).contains("Sample"));
        assertTrue(Files.readString(projectPath.resolve(".gitignore")).contains(".bettergit/diagrams/"));
    }

    @Test
    void disabledFeatureDoesNotInvokeTheGenerator() throws Exception {
        configure(FeatureSettings.disabled());
        CommitDiagramService service = service((source, output) -> {
            throw new AssertionError("disabled diagram generator was invoked");
        });

        Optional<CommitDiagramPlan> plan = service.planForCommit(projectPath, COMMIT_HASH);

        assertTrue(plan.isEmpty());
        assertFalse(Files.exists(projectPath.resolve(".bettergit/diagrams")));
    }

    @Test
    void rejectsMissingJavaSourcesAndSymbolicOutputFiles() throws Exception {
        configure(new FeatureSettings(true, false, false));
        CommitDiagramService service = service((source, output) -> Files.writeString(output, "<svg/>"));

        assertThrows(java.io.IOException.class,
                () -> service.planForCommit(projectPath, COMMIT_HASH));

        Files.createDirectories(projectPath.resolve("src/main/java"));
        Path diagrams = Files.createDirectories(projectPath.resolve(".bettergit/diagrams"));
        Path redirected = Files.writeString(projectPath.resolve("redirected.svg"), "unchanged");
        Files.createSymbolicLink(diagrams.resolve(COMMIT_HASH + ".svg"), redirected);

        CommitDiagramPlan plan = service.planForCommit(projectPath, COMMIT_HASH).orElseThrow();
        assertThrows(java.io.IOException.class, () -> service.generate(plan));
        assertEquals("unchanged", Files.readString(redirected));
    }

    private CommitDiagramService service(ClassDiagramGenerator generator) {
        return new CommitDiagramService(
                generator, new BetterGitConfigurationLoader(), new BetterGitFileStore());
    }

    private void configure(FeatureSettings features) throws Exception {
        BetterGitConfiguration configuration = new BetterGitConfiguration(
                2, "2026-08-21T12:00:00Z", projectPath.toString(), true, true,
                features, false, AiCommitSettings.disabled(null));
        new BetterGitFileStore().writeInitialization(projectPath, configuration, "# Test\n");
    }
}
