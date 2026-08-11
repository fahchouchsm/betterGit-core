package io.fahchouchsm.betterGitCore.ai.commitdocumentar;

import io.fahchouchsm.betterGitCore.ai.AiSystem;

import java.io.IOException;
import java.util.Objects;

/** Generates documentation for a Git commit using {@link AiSystem}. */
public final class CommitDocumentar {
    private final AiSystem aiSystem;

    public CommitDocumentar(AiSystem aiSystem) {
        this.aiSystem = Objects.requireNonNull(aiSystem, "aiSystem must not be null");
    }

    public String document(String commitDiff) throws IOException, InterruptedException {
        return aiSystem.generate("""
                Write concise documentation for this Git commit. Explain the purpose,
                key changes, and any important usage or migration notes. If details are
                not present in the diff, say so instead of inventing them.

                Commit diff:
                """ + commitDiff);
    }
}
