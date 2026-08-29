package io.fahchouchsm.betterGitCore.sonarqube;

import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitDirectories;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeConfiguration;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class SonarQubeService {
    private final SonarQubeServiceDependencies dependencies;

    public SonarQubeService(SonarQubeServiceDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public Optional<SonarAnalysisOutcome> analyze(
            Path projectPath, SonarQubeEvent event, String branch) throws IOException, InterruptedException {
        Optional<BetterGitConfiguration> stored = dependencies.betterGitConfigurationLoader().load(projectPath);
        if (stored.isEmpty() || !stored.orElseThrow().settings().sonarQubeDocumentation()) {
            return Optional.empty();
        }
        SonarQubeSettings settings = stored.orElseThrow().settings().sonarQube();
        if (!applies(settings, event, branch)) {
            return Optional.empty();
        }
        SonarQubeConfiguration configuration = dependencies.sonarQubeConfigurationLoader()
                .load(projectPath, settings, dependencies.environment());
        Optional<String> validationError = configuration.validationError();
        if (validationError.isPresent()) {
            return Optional.of(analysisFailure(configuration, validationError.orElseThrow()
                    + " Run 'bettergit features' to update the integration."));
        }
        return Optional.of(runAnalysis(projectPath, configuration));
    }

    public boolean isEnabledFor(Path projectPath, SonarQubeEvent event, String branch) throws IOException {
        Optional<BetterGitConfiguration> stored = dependencies.betterGitConfigurationLoader().load(projectPath);
        if (stored.isEmpty() || !stored.orElseThrow().settings().sonarQubeDocumentation()) {
            return false;
        }
        return applies(stored.orElseThrow().settings().sonarQube(), event, branch);
    }

    private SonarAnalysisOutcome runAnalysis(
            Path projectPath, SonarQubeConfiguration configuration)
            throws IOException, InterruptedException {
        Path metadataFile = metadataFile(projectPath);
        Files.deleteIfExists(metadataFile);
        int scannerExitCode = dependencies.scanner().run(
                new SonarAnalysisRequest(projectPath, configuration, metadataFile));
        if (!Files.isRegularFile(metadataFile)) {
            return analysisFailure(configuration,
                    "SonarQube analysis did not produce report-task.txt (exit code " + scannerExitCode + ").");
        }
        String dashboardUrl = metadata(metadataFile).get("dashboardUrl");
        try {
            SonarQualityGate qualityGate = dependencies.qualityGateClient().fetch(configuration);
            return qualityGateOutcome(qualityGate, dashboardUrl, configuration);
        } catch (IOException exception) {
            String message = scannerExitCode == 0
                    ? "Analysis finished, but the quality-gate result could not be read: " + exception.getMessage()
                    : "SonarQube analysis failed with exit code " + scannerExitCode + ".";
            return analysisFailure(configuration, message, dashboardUrl);
        }
    }

    private static SonarAnalysisOutcome qualityGateOutcome(
            SonarQualityGate gate, String dashboardUrl, SonarQubeConfiguration configuration) {
        SonarAnalysisOutcome.Status status = gate.passed()
                ? SonarAnalysisOutcome.Status.PASSED
                : SonarAnalysisOutcome.Status.QUALITY_GATE_FAILED;
        return new SonarAnalysisOutcome(
                status, gate.conditions(), dashboardUrl, null, configuration.failurePolicy());
    }

    private static SonarAnalysisOutcome analysisFailure(
            SonarQubeConfiguration configuration, String message) {
        return analysisFailure(configuration, message, null);
    }

    private static SonarAnalysisOutcome analysisFailure(
            SonarQubeConfiguration configuration, String message, String dashboardUrl) {
        return new SonarAnalysisOutcome(
                SonarAnalysisOutcome.Status.ANALYSIS_FAILED, java.util.List.of(),
                dashboardUrl, message, configuration.failurePolicy());
    }

    private static boolean applies(SonarQubeSettings settings, SonarQubeEvent event, String branch) {
        boolean eventEnabled = event == SonarQubeEvent.COMMIT
                ? settings.trigger().includesCommit()
                : settings.trigger().includesMerge();
        return eventEnabled && settings.appliesToBranch(branch);
    }

    private static Path metadataFile(Path projectPath) throws IOException {
        return BetterGitDirectories.child(projectPath, "sonarqube").resolve("report-task.txt");
    }

    private static Map<String, String> metadata(Path metadataFile) throws IOException {
        if (!Files.isRegularFile(metadataFile)) {
            return Map.of();
        }
        return Files.readAllLines(metadataFile).stream()
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(java.util.stream.Collectors.toMap(parts -> parts[0], parts -> parts[1]));
    }
}
