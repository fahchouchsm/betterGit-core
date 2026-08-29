package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConfirmationDefault;
import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.configuration.SonarQubeFailurePolicy;
import io.fahchouchsm.betterGitCore.sonarqube.SonarAnalysisOutcome;
import io.fahchouchsm.betterGitCore.sonarqube.SonarCondition;
import io.fahchouchsm.betterGitCore.sonarqube.SonarQubeEvent;
import io.fahchouchsm.betterGitCore.sonarqube.SonarQubeService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

final class SonarQubeGate {
    private final SonarQubeService service;
    private final ConsolePort console;

    SonarQubeGate(SonarQubeService service, ConsolePort console) {
        this.service = service;
        this.console = console;
    }

    boolean runAndApprove(Path projectPath, SonarQubeEvent event, String branch)
            throws IOException, InterruptedException {
        Optional<SonarAnalysisOutcome> analysis = service.analyze(projectPath, event, branch);
        if (analysis.isEmpty()) {
            return true;
        }
        SonarAnalysisOutcome outcome = analysis.orElseThrow();
        show(outcome);
        if (outcome.passed()) {
            return true;
        }
        if (outcome.failurePolicy() == SonarQubeFailurePolicy.CANCEL) {
            return false;
        }
        return console.confirm(
                "SonarQube did not pass. Continue anyway?", ConfirmationDefault.NO);
    }

    boolean isEnabledFor(Path projectPath, SonarQubeEvent event, String branch) throws IOException {
        return service.isEnabledFor(projectPath, event, branch);
    }

    private void show(SonarAnalysisOutcome outcome) {
        if (outcome.passed()) {
            console.success("SonarQube quality gate passed.");
        } else if (outcome.status() == SonarAnalysisOutcome.Status.QUALITY_GATE_FAILED) {
            console.warning("SonarQube quality gate failed.");
        } else {
            console.warning(outcome.failureMessage());
        }
        outcome.conditions().stream()
                .filter(condition -> !"OK".equalsIgnoreCase(condition.status()))
                .forEach(condition -> console.warning(conditionText(condition)));
        if (outcome.dashboardUrl() != null) {
            console.info("SonarQube dashboard: " + outcome.dashboardUrl());
        }
    }

    private static String conditionText(SonarCondition condition) {
        String threshold = condition.errorThreshold() == null ? "" : " (limit " + condition.errorThreshold() + ")";
        return "  " + condition.metricKey() + ": " + condition.actualValue() + threshold;
    }
}
