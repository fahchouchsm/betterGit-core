package io.fahchouchsm.betterGitCore.documentation;

import io.fahchouchsm.betterGitCore.ai.AiSystem;
import io.fahchouchsm.betterGitCore.configuration.AiConfiguration;

import java.io.IOException;

/** Uses the existing BetterGit AI client to generate documentation text. */
public final class AiSystemTextGenerator implements AiTextGenerator {
    @Override
    public String generate(AiConfiguration configuration, String prompt) throws IOException, InterruptedException {
        return new AiSystem(configuration).generate(prompt);
    }
}
