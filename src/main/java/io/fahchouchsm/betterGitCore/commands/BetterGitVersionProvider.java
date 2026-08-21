package io.fahchouchsm.betterGitCore.commands;

import picocli.CommandLine.IVersionProvider;

public final class BetterGitVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        String implementationVersion = BetterGitCommand.class.getPackage().getImplementationVersion();
        String version = implementationVersion == null ? "development" : implementationVersion;
        return new String[]{"BetterGit " + version};
    }
}
