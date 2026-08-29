package io.fahchouchsm.betterGitCore.sonarqube;

import io.fahchouchsm.betterGitCore.configuration.AiCommitSettings;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.configuration.FeatureSettings;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeFailurePolicy;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeSettings;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarQubeServiceTest {
    @TempDir
    Path projectPath;

    @Test
    void returnsFailedConditionsAndDashboardForAnEnabledCommit() throws Exception {
        configure(new SonarQubeSettings(
                "https://sonar.example", "bettergit", SonarQubeTrigger.COMMITS,
                List.of("main"), SonarQubeFailurePolicy.ASK_FOR_APPROVAL));
        SonarScanner scanner = request -> {
            Files.createDirectories(request.metadataFile().getParent());
            Files.writeString(request.metadataFile(), "dashboardUrl=https://sonar.example/dashboard?id=bettergit\n");
            return 1;
        };
        SonarQualityGateClient client = configuration -> new SonarQualityGate(
                "ERROR", List.of(new SonarCondition("new_coverage", "ERROR", "72.0", "80")));

        SonarAnalysisOutcome outcome = service(scanner, client)
                .analyze(projectPath, SonarQubeEvent.COMMIT, "main").orElseThrow();

        assertEquals(SonarAnalysisOutcome.Status.QUALITY_GATE_FAILED, outcome.status());
        assertEquals("new_coverage", outcome.conditions().getFirst().metricKey());
        assertEquals("https://sonar.example/dashboard?id=bettergit", outcome.dashboardUrl());
    }

    @Test
    void skipsEventsAndBranchesOutsideTheConfiguredScope() throws Exception {
        configure(new SonarQubeSettings(
                "https://sonar.example", "bettergit", SonarQubeTrigger.COMMITS,
                List.of("release"), SonarQubeFailurePolicy.CANCEL));
        AtomicInteger scannerRuns = new AtomicInteger();
        SonarQubeService service = service(request -> scannerRuns.incrementAndGet(),
                configuration -> new SonarQualityGate("OK", List.of()));

        assertTrue(service.analyze(projectPath, SonarQubeEvent.MERGE, "release").isEmpty());
        assertTrue(service.analyze(projectPath, SonarQubeEvent.COMMIT, "main").isEmpty());
        assertFalse(service.isEnabledFor(projectPath, SonarQubeEvent.COMMIT, "main"));
        assertEquals(0, scannerRuns.get());
    }

    @Test
    void missingScannerMetadataBlocksWithoutReadingAStaleGate() throws Exception {
        configure(new SonarQubeSettings(
                "https://sonar.example", "bettergit", SonarQubeTrigger.COMMITS_AND_MERGES,
                List.of(), SonarQubeFailurePolicy.ASK_FOR_APPROVAL));
        AtomicInteger apiCalls = new AtomicInteger();
        SonarQubeService service = service(request -> 1, configuration -> {
            apiCalls.incrementAndGet();
            return new SonarQualityGate("OK", List.of());
        });

        SonarAnalysisOutcome outcome = service
                .analyze(projectPath, SonarQubeEvent.COMMIT, "main").orElseThrow();

        assertEquals(SonarAnalysisOutcome.Status.ANALYSIS_FAILED, outcome.status());
        assertTrue(outcome.failureMessage().contains("report-task.txt"));
        assertEquals(0, apiCalls.get());
    }

    private SonarQubeService service(SonarScanner scanner, SonarQualityGateClient client) {
        return new SonarQubeService(new SonarQubeServiceDependencies(
                new BetterGitConfigurationLoader(), new SonarQubeConfigurationLoader(), scanner, client,
                Map.of(SonarQubeConfigurationLoader.TOKEN, "secret-token")));
    }

    private void configure(SonarQubeSettings sonarQube) throws Exception {
        FeatureSettings features = new FeatureSettings(false, false, true, sonarQube);
        BetterGitConfiguration configuration = new BetterGitConfiguration(
                3, "2026-08-29T12:00:00Z", projectPath.toString(), true, true,
                features, false, AiCommitSettings.disabled(null));
        new BetterGitFileStore().writeInitialization(projectPath, configuration, "# Project\n");
    }
}
