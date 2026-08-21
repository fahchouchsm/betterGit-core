package io.fahchouchsm.betterGitCore.documentation;

import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;

import java.io.IOException;

/** AI text boundary used by BetterGit documentation workflows. */
public interface AiTextGenerator {
    String generate(AiConfiguration configuration, String prompt) throws IOException, InterruptedException;
}
