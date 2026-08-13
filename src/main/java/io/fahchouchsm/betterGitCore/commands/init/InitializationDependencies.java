package io.fahchouchsm.betterGitCore.commands.init;

import io.fahchouchsm.betterGitCore.commands.RepositoryAccess;
import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.documentation.ProjectDocumentationGenerator;
import io.fahchouchsm.betterGitCore.project.JavaProjectDetector;
import io.fahchouchsm.betterGitCore.project.MarkdownProjectScanner;

import java.time.Clock;
import java.util.Map;

/** Injected collaborators for the init workflow. */
public record InitializationDependencies(
        RepositoryAccess repositoryAccess,
        ConsolePort console,
        AiConfigurationLoader aiConfigurationLoader,
        JavaProjectDetector javaProjectDetector,
        MarkdownProjectScanner markdownProjectScanner,
        ProjectDocumentationGenerator documentationGenerator,
        BetterGitFileStore fileStore,
        Map<String, String> environment,
        Clock clock) {

    @Override
    public String toString() {
        return "InitializationDependencies[environment=[REDACTED]]";
    }
}
