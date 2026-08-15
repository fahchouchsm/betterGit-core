package io.fahchouchsm.betterGitCore.commands;

import java.nio.file.Path;

final class ProjectDirectoryResolver {
    private ProjectDirectoryResolver() {
    }

    static Path resolve(Path invocationDirectory, Path requestedDirectory) {
        Path normalizedInvocation = invocationDirectory.toAbsolutePath().normalize();
        return requestedDirectory == null
                ? normalizedInvocation
                : normalizedInvocation.resolve(requestedDirectory).normalize();
    }
}
