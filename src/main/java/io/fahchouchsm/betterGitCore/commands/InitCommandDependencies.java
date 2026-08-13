package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.configuration.AiConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;
import io.fahchouchsm.betterGitCore.documentation.ProjectDocumentationGenerator;
import io.fahchouchsm.betterGitCore.project.JavaProjectDetector;
import io.fahchouchsm.betterGitCore.project.MarkdownProjectScanner;

import java.time.Clock;
import java.util.Map;

public record InitCommandDependencies(
        RepositoryAccess repositoryAccess,
        CommandConsole console,
        AiConfigurationLoader aiConfigurationLoader,
        JavaProjectDetector javaProjectDetector,
        MarkdownProjectScanner markdownProjectScanner,
        ProjectDocumentationGenerator documentationGenerator,
        BetterGitFileStore fileStore,
        Map<String, String> environment,
        Clock clock) {
}
