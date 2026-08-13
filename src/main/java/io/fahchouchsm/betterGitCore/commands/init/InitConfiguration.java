package io.fahchouchsm.betterGitCore.commands.init;

import java.nio.file.Path;

/** Inputs that control one BetterGit initialization run. */
public record InitConfiguration(Path projectPath, InitializationMode mode) {
}
